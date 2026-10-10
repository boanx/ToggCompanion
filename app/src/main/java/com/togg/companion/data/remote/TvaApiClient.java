package com.togg.companion.data.remote;

import com.togg.companion.core.TelemetryParser;
import com.togg.companion.core.ToggAuthManager;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.core.GezRouteBridge;
import com.togg.companion.data.SecureTokenStorage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.OutputStream;

/**
 * Handles communication with Togg TVA (Telematics & Vehicle Access) cloud API.
 * Uses official Trumore widget endpoint and verified headers.
 *
 * Strategies:
 *   1. GET /info/api/v2/VehicleInfo?vin=XXX
 *   2. GET /info/api/v2/FriendVehicleInfo?vin=XXX (for shared/family drivers)
 */
public final class TvaApiClient {
    public interface DestinationCallback { void onSuccess(String message); void onError(String message); }
    public static final String PLANNED_JOURNEYS_ENDPOINT = "https://superapp-api.channels.togg.cloud/j/api/PlannedJourneys";

    public interface TelemetryCallback {
        void onSuccess(VehicleTelemetry telemetry, boolean isLive);
        void onError(String message, VehicleTelemetry fallback);
    }

    private static final int STRATEGY_PRIMARY_VIN = 0;  // /info/api/v2/VehicleInfo?vin=XXX
    private static final int STRATEGY_FRIEND_VIN  = 1;  // /info/api/v2/FriendVehicleInfo?vin=XXX

    private final SecureTokenStorage storage;

    public TvaApiClient(SecureTokenStorage storage) {
        this.storage = storage;
    }

    public void sendDestinationToVehicle(double lat, double lon, String name, DestinationCallback callback) {
        new Thread(() -> sendDestinationRequest(lat, lon, name, null, callback, true), "togg-planned-journey").start();
    }

    public void sendRouteToVehicle(GezRouteBridge.GezPoint route, DestinationCallback callback) {
        if (route == null) { callback.onError("Rota hedefi bulunamadı."); return; }
        new Thread(() -> sendDestinationRequest(route.lat, route.lon, route.name, route, callback, true), "togg-planned-route").start();
    }

    private void sendDestinationRequest(double lat, double lon, String name, GezRouteBridge.GezPoint route, DestinationCallback callback, boolean allowRefresh) {
        if (!storage.hasValidToken() || storage.isDemoMode()) {
            callback.onError("Togg ID ile giriş yapın ve aracın VIN numarasını ayarlayın.");
            return;
        }
        String vin = storage.getVin();
        if (vin == null || vin.trim().isEmpty() || storage.isDemoVin()) {
            callback.onError("Gerçek araç VIN numarası olmadan hedef gönderilemez.");
            return;
        }
        HttpURLConnection connection = null;
        try {
            JSONObject destination = new JSONObject();
            destination.put("latitude", lat); destination.put("longitude", lon);
            destination.put("name", name == null || name.trim().isEmpty() ? "Hedef" : name.trim());
            JSONObject payload = new JSONObject();
            payload.put("vin", vin.trim()); payload.put("destination", destination);
            if (route != null && route.hasMultipleStops()) {
                JSONArray stops = new JSONArray();
                for (GezRouteBridge.GezPoint stop : route.stops) {
                    JSONObject item = new JSONObject();
                    item.put("latitude", stop.lat); item.put("longitude", stop.lon); item.put("name", stop.name == null ? "" : stop.name);
                    stops.put(item);
                }
                payload.put("stops", stops);
            }
            connection = (HttpURLConnection) new URL(PLANNED_JOURNEYS_ENDPOINT).openConnection();
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setDoOutput(true); connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + storage.getAccessToken());
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int code = connection.getResponseCode();
            if (code == 401 && allowRefresh && !storage.getRefreshToken().isEmpty()) {
                connection.disconnect(); connection = null;
                ToggAuthManager.refreshAccessToken(storage.getRefreshToken(), new ToggAuthManager.TokenCallback() {
                    @Override public void onSuccess(String accessToken, String refreshToken) {
                        storage.setAccessToken(accessToken); storage.setRefreshToken(refreshToken);
                        sendDestinationRequest(lat, lon, name, route, callback, false);
                    }
                    @Override public void onError(String message) { callback.onError("Oturum yenilenemedi. Lütfen Togg ID ile yeniden giriş yapın."); }
                });
                return;
            }
            InputStream response = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseText = response == null ? "" : readStream(response);
            if (code >= 200 && code < 300) callback.onSuccess("Hedef Togg aracınıza iletildi.");
            else callback.onError("Togg Cloud hedef gönderimi başarısız (HTTP " + code + "). " + responseText);
        } catch (Exception error) { callback.onError("Togg Cloud bağlantı hatası: " + error.getMessage()); }
        finally { if (connection != null) connection.disconnect(); }
    }

    public void fetchTelemetry(TelemetryCallback callback) {
        new Thread(() -> {
            if (storage.isDemoMode() || !storage.hasValidToken()) {
                VehicleTelemetry demo = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
                callback.onSuccess(demo, false);
                return;
            }

            // If user has a valid token but hasn't entered their real vehicle VIN yet:
            if (storage.isDemoVin()) {
                VehicleTelemetry fallback = TelemetryParser.createDemoSnapshot("Şasi No Bekleniyor", storage.getPlate());
                callback.onError("Şasi Numarası (VIN) Gerekli:\n\n"
                        + "Togg ID hesabınıza başarıyla bağlandınız. Canlı araç verilerini çekebilmek için lütfen Trumore uygulamasındaki 'Akıllı Cihazım' sekmesinde veya ruhsatınızda yer alan 17 haneli şasi numaranızı (NL1...) giriniz.", fallback);
                return;
            }

            executeFetch(callback, true, storage.getOwnerId().isEmpty() ? STRATEGY_PRIMARY_VIN : STRATEGY_FRIEND_VIN);
        }).start();
    }

    private void executeFetch(TelemetryCallback callback, boolean allowTokenRefresh, int strategy) {
        HttpURLConnection conn = null;
        try {
            String baseEndpoint = storage.getApiEndpoint();
            String vin = storage.getVin() != null ? storage.getVin().trim() : "";
            String token = storage.getAccessToken();

            String requestUrl;
            if (strategy == STRATEGY_FRIEND_VIN) {
                requestUrl = baseEndpoint.replace("/VehicleInfo", "/FriendVehicleInfo");
                String ownerId = storage.getOwnerId() != null ? storage.getOwnerId().trim() : "";
                if (!ownerId.isEmpty()) {
                    requestUrl += (requestUrl.contains("?") ? "&" : "?") + "ownerId=" + ownerId;
                }
            } else {
                requestUrl = baseEndpoint;
            }

            if (!vin.isEmpty()) {
                requestUrl += (requestUrl.contains("?") ? "&" : "?") + "vin=" + vin;
            }

            URL url = new URL(requestUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Trumore Home Widget");
            conn.setRequestProperty("x-app-version", "661");
            conn.setRequestProperty("Authorization", "Bearer " + token);

            int code = conn.getResponseCode();

            // Read error stream if available
            String errorDetail = "";
            try {
                InputStream err = conn.getErrorStream();
                if (err != null) {
                    errorDetail = readStream(err);
                }
            } catch (Exception ignored) {}

            // === SUCCESS (200-299) ===
            if (code >= 200 && code < 300) {
                String body = readStream(conn.getInputStream());
                VehicleTelemetry parsed = TelemetryParser.parse(body, storage.getVin(), storage.getPlate());
                if (parsed.vin != null && !parsed.vin.isEmpty() && !parsed.vin.equalsIgnoreCase("NL1CFB0P800100522")) {
                    storage.setVin(parsed.vin);
                }
                if (parsed.timestampMillis > 0) {
                    storage.setLastSync(parsed.timestampMillis);
                }
                callback.onSuccess(parsed, true);
                return;
            }

            // === 401: Check whether it's VIN permission error vs Token Expiry ===
            String errorLower = errorDetail.toLowerCase();
            boolean isPermissionError = errorLower.contains("permission")
                    || errorLower.contains("access")
                    || errorLower.contains("yetki")
                    || errorLower.contains("unauthorized resource")
                    || errorLower.contains("don't have permission");

            // If it's a 401 due to vehicle access permission, do NOT loop token refresh!
            if (code == 401 && isPermissionError && !storage.getOwnerId().isEmpty()) {
                // If primary endpoint denied, try FriendVehicleInfo before giving up
                if (strategy == STRATEGY_PRIMARY_VIN) {
                    executeFetch(callback, allowTokenRefresh, STRATEGY_FRIEND_VIN);
                    return;
                }

                String msg = "Şasi Numarası Yetki Hatası (HTTP 401): Togg sunucusu hesabınızın '" + vin + "' şasi numaralı araca erişim izni olmadığını bildirdi.\n\n"
                        + "Lütfen Trumore uygulamasındaki 'Akıllı Cihazım' sekmesinden veya ruhsatınızdan 17 haneli şasi numaranızı (VIN) kontrol edip giriniz.";
                if (!errorDetail.isEmpty() && errorDetail.length() < 250) {
                    msg += "\nDetay: " + errorDetail;
                }
                VehicleTelemetry fallback = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
                callback.onError(msg, fallback);
                return;
            }

            // If token expired, try refresh once
            if (code == 401 && allowTokenRefresh && !storage.getRefreshToken().isEmpty()) {
                ToggAuthManager.refreshAccessToken(storage.getRefreshToken(), new ToggAuthManager.TokenCallback() {
                    @Override
                    public void onSuccess(String newAccessToken, String newRefreshToken) {
                        storage.setAccessToken(newAccessToken);
                        storage.setRefreshToken(newRefreshToken);
                        executeFetch(callback, false, strategy);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        VehicleTelemetry fallback = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
                        callback.onError("Oturum Süresi Doldu (HTTP 401): Togg ID oturumunuzun süresi doldu. Lütfen yeniden giriş yapınız.", fallback);
                    }
                });
                return;
            }

            // Try FriendVehicleInfo on 404
            if (code == 404 && strategy == STRATEGY_PRIMARY_VIN && !storage.getOwnerId().isEmpty()) {
                executeFetch(callback, allowTokenRefresh, STRATEGY_FRIEND_VIN);
                return;
            }

            // Other errors
            String msg;
            if (code == 404) {
                msg = "Araç Bulunamadı (HTTP 404): '" + vin + "' şasi numaralı araç hesabınızda birincil araç olarak kayıtlı görünmüyor.\n\n"
                        + "• Kendi aracınız ise: Ayarlar menüsünden 17 haneli şasi numaranızı (VIN) kontrol ediniz.\n"
                        + "• Başkası tarafından sizinle paylaşılan bir araç ise: Ayarlar menüsünden 'Araç Sahibi Kimliği (Owner ID)' bilgisini giriniz.";
            } else if (code == 401) {
                msg = "Oturum Süresi Doldu (HTTP 401): Togg ID oturumunuz doğrulanamadı. Lütfen 'Togg ID ile Giriş Yap' butonuna basarak yeniden bağlanın.";
                if (!errorDetail.isEmpty() && errorDetail.length() < 200) {
                    msg += " (" + errorDetail + ")";
                }
            } else if (code == 500) {
                msg = "Sunucu Hatası (HTTP 500): Togg TVA sunucusu isteğe yanıt veremedi.";
                if (!errorDetail.isEmpty() && errorDetail.length() < 200) {
                    msg += "\nDetay: " + errorDetail;
                }
            } else {
                msg = "Sunucu yanıtı: HTTP " + code;
                if (!errorDetail.isEmpty() && errorDetail.length() < 200) {
                    msg += " (" + errorDetail + ")";
                }
            }

            VehicleTelemetry fallback = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
            callback.onError(msg, fallback);

        } catch (Exception e) {
            VehicleTelemetry fallback = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
            callback.onError("Bağlantı hatası: " + e.getMessage(), fallback);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
