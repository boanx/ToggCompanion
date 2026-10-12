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
import org.json.JSONException;
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
    public interface DestinationCallback {
        void onSuccess(String message);
        void onError(String message);
        default void onUncertain(String message) { onError(message); }
    }
    // Trumore prod NAVIGATION_API_URL. SUPERAPP_URL is not the navigation API.
    public static final String PLANNED_JOURNEYS_ENDPOINT = "https://navigation-api.togg.cloud/j/api/PlannedJourneys";

    public interface TelemetryCallback {
        void onSuccess(VehicleTelemetry telemetry, boolean isLive);
        void onError(String message, VehicleTelemetry fallback);
    }

    private static final int STRATEGY_PRIMARY_VIN = 0;  // /info/api/v2/VehicleInfo?vin=XXX
    private static final int STRATEGY_FRIEND_VIN  = 1;  // /info/api/v2/FriendVehicleInfo?vin=XXX

    // Serialize manual and background route submissions in this process.
    private static final java.util.concurrent.atomic.AtomicBoolean DESTINATION_IN_FLIGHT = new java.util.concurrent.atomic.AtomicBoolean();
    private final SecureTokenStorage storage;

    public TvaApiClient(SecureTokenStorage storage) {
        this.storage = storage;
    }

    public void sendDestinationToVehicle(double lat, double lon, String name, DestinationCallback callback) {
        sendRouteToVehicle(new GezRouteBridge.GezPoint(lat, lon, name), callback);
    }

    public void sendRouteToVehicle(GezRouteBridge.GezPoint route, DestinationCallback callback) {
        if (callback == null) return;
        if (route == null) { callback.onError("Rota hedefi bulunamadı."); return; }
        if (!DESTINATION_IN_FLIGHT.compareAndSet(false, true)) {
            callback.onError("Başka bir rota gönderimi sürüyor. Sonucunu bekleyin."); return;
        }
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        DestinationCallback once = new DestinationCallback() {
            @Override public void onSuccess(String message) {
                if (completed.compareAndSet(false, true)) {
                    DESTINATION_IN_FLIGHT.set(false); callback.onSuccess(message);
                }
            }
            @Override public void onError(String message) {
                if (completed.compareAndSet(false, true)) {
                    DESTINATION_IN_FLIGHT.set(false); callback.onError(message);
                }
            }
            @Override public void onUncertain(String message) {
                if (completed.compareAndSet(false, true)) {
                    DESTINATION_IN_FLIGHT.set(false); callback.onUncertain(message);
                }
            }
        };
        new Thread(() -> {
            try { sendDestinationRequest(route.lat, route.lon, route.name, route, once, true); }
            catch (Exception error) { once.onError("Rota gönderimi hazırlanamadı. Oturumu ve araç bilgisini kontrol edin."); }
        }, "togg-planned-route").start();
    }

    private void sendDestinationRequest(double lat, double lon, String name, GezRouteBridge.GezPoint route, DestinationCallback callback, boolean allowRefresh) {
        if (!storage.hasValidToken() || storage.isDemoMode()) {
            callback.onError("Togg ID ile giriş yapın ve aracın VIN numarasını ayarlayın."); return;
        }
        String vin = storage.getVin();
        if (vin == null || vin.trim().isEmpty() || storage.isDemoVin()) {
            callback.onError("Gerçek araç VIN numarası olmadan hedef gönderilemez."); return;
        }
        final String requestToken = storage.getAccessToken();
        final String refreshToken = storage.getRefreshToken();
        HttpURLConnection connection = null;
        try {
            VehicleTelemetry cached = storage.getActiveTelemetry();
            GezRouteBridge.GezPoint origin = cached != null && vin.trim().equals(cached.vin)
                    && validJourneyCoordinates(cached.latitude, cached.longitude)
                    ? new GezRouteBridge.GezPoint(cached.latitude, cached.longitude,
                        cached.parkAddress == null || cached.parkAddress.trim().isEmpty() ? "Başlangıç" : cached.parkAddress)
                    : null;
            JSONObject payload = buildPlannedJourneyPayload(
                    route == null ? new GezRouteBridge.GezPoint(lat, lon, name) : route, origin);
            if (!sameDestinationSession(vin, requestToken)) {
                callback.onError("Hesap veya araç değişti. Rota gönderimini yeniden başlatın."); return;
            }
            connection = (HttpURLConnection) new URL(PLANNED_JOURNEYS_ENDPOINT).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setDoOutput(true); connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + requestToken);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int code = connection.getResponseCode();
            String responseText = "";
            InputStream response = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (response != null) try (InputStream stream = response) { responseText = readStream(stream); }
            if (!sameDestinationSession(vin, requestToken)) {
                callback.onError("Gönderim sırasında hesap veya araç değişti. Sonuç bu araç için doğrulanamadı; araç ekranını kontrol edin."); return;
            }
            boolean permissionDenied = destinationPermissionDenied(responseText);
            if (code == 401 && !permissionDenied && allowRefresh && refreshToken != null && !refreshToken.isEmpty()) {
                connection.disconnect(); connection = null;
                ToggAuthManager.refreshAccessToken(refreshToken, new ToggAuthManager.TokenCallback() {
                    @Override public void onSuccess(String accessToken, String newRefreshToken) {
                        // Never replace credentials after account/vehicle changes or another refresh.
                        if (!sameDestinationSession(vin, requestToken) || !requestToken.equals(storage.getAccessToken())
                                || !destinationAccount(requestToken).equals(destinationAccount(accessToken))) {
                            callback.onError("Oturum veya araç bilgisi değişti. Gönderimi yeniden deneyin."); return;
                        }
                        try {
                            storage.setAccessToken(accessToken); storage.setRefreshToken(newRefreshToken);
                            sendDestinationRequest(lat, lon, name, route, callback, false);
                        } catch (Exception error) {
                            callback.onError("Yenilenen oturum kaydedilemedi. Togg ID ile tekrar giriş yapın.");
                        }
                    }
                    @Override public void onError(String message) { callback.onError("Oturum yenilenemedi. Togg ID ile yeniden giriş yapın."); }
                });
                return;
            }
            if (code >= 200 && code < 300) {
                callback.onSuccess("Rota Togg Cloud tarafından kabul edildi (HTTP " + code + ").");
            } else if (code >= 300 && code < 400 && destinationHomeRedirect(connection.getHeaderField("Location"))) {
                callback.onError("Rota gönderimi başarısız (HTTP " + code + "). Sunucu rota API'si yerine ana sayfaya (/home) yönlendirdi. Rota aktarımı tamamlanmadı.");
            } else callback.onError(destinationHttpError(code, permissionDenied));
        } catch (java.net.SocketTimeoutException error) {
            callback.onUncertain("Gönderim yanıtı zamanında alınamadı. Hedef ulaşmış olabilir; yeniden göndermeden önce araç ekranını kontrol edin.");
        } catch (IllegalArgumentException error) {
            callback.onError("Rota koordinatları geçersiz. Hedefi yeniden önizleyin.");
        } catch (Exception error) {
            // Do not expose raw server bodies, credentials, account IDs or exception URLs.
            callback.onUncertain("Gönderim sonucu alınamadı. İnternet bağlantısını ve araç ekranını kontrol edin; otomatik tekrar gönderim yapılmadı.");
        } finally { if (connection != null) connection.disconnect(); }
    }

    private boolean sameDestinationSession(String vin, String token) {
        if (storage.isDemoMode() || !storage.hasValidToken() || !vin.equals(storage.getVin())) return false;
        return destinationAccount(token).equals(destinationAccount(storage.getAccessToken()));
    }

    private static String destinationAccount(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                JSONObject claims = new JSONObject(new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
                String subject = claims.optString("sub", "");
                if (!subject.isEmpty()) return "sub:" + claims.optString("iss", "") + ":" + subject;
            }
        } catch (Exception ignored) { }
        return "token:" + token;
    }

    static boolean destinationPermissionDenied(String body) {
        String value = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        return value.contains("permission") || value.contains("forbidden") || value.contains("yetki")
                || value.contains("access denied") || value.contains("unauthorized resource");
    }

    private static boolean destinationHomeRedirect(String location) {
        if (location == null || location.trim().isEmpty()) return false;
        try {
            // Diagnostic only. Never forward Authorization or POST data to the redirect.
            String path = new URL(new URL(PLANNED_JOURNEYS_ENDPOINT), location).getPath();
            return "/home".equals(path) || "/home/".equals(path);
        } catch (java.net.MalformedURLException invalid) { return false; }
    }

    static String destinationHttpError(int code, boolean permissionDenied) {
        String prefix = "Rota gönderimi başarısız (HTTP " + code + "). ";
        if (permissionDenied || code == 403) return prefix + "Hesabın araç veya navigasyon erişim iznini kontrol edin.";
        if (code == 401) return prefix + "Togg ID ile yeniden giriş yapın.";
        if (code == 400 || code == 422) return prefix + "Hedef veya başlangıç bilgisi kabul edilmedi. Canlı araç konumunu güncelleyip tekrar deneyin.";
        if (code == 429) return prefix + "Çok sık gönderim yapıldı. Bir süre bekleyip tekrar deneyin.";
        if (code >= 500) return prefix + "Togg hizmeti yanıt veremiyor. Daha sonra tekrar deneyin.";
        if (code >= 300 && code < 400) return prefix + "Beklenmeyen sunucu yönlendirmesi alındı. İstek başka adrese gönderilmedi.";
        return prefix + "Hesap, internet bağlantısı ve hedef bilgisini kontrol edin.";
    }

    /** Shape recovered from Trumore 2.1.25's PlannedJourneyRequest serializer. */
    static JSONObject buildPlannedJourneyPayload(GezRouteBridge.GezPoint route,
                                                 GezRouteBridge.GezPoint currentOrigin) throws JSONException {
        if (route == null) throw new IllegalArgumentException("Rota hedefi bulunamadı.");
        JSONObject payload = new JSONObject();
        GezRouteBridge.GezPoint arrival = route;
        GezRouteBridge.GezPoint start = currentOrigin;
        if (route.hasMultipleStops()) {
            if (route.stopsIncludeOrigin) start = route.stops.get(0);
            arrival = route.stops.get(route.stops.size() - 1);
            int firstWaypoint = route.stopsIncludeOrigin ? 1 : 0;
            if (route.stops.size() - firstWaypoint > 1) {
                JSONArray waypoints = new JSONArray();
                for (int i = firstWaypoint; i < route.stops.size() - 1; i++) {
                    waypoints.put(buildJourneyPoint(route.stops.get(i)));
                }
                payload.put("waypoints", waypoints);
            }
        }
        if (start != null && validJourneyCoordinates(start.lat, start.lon)) {
            payload.put("startingPoint", buildJourneyPoint(start));
        } else if (route.hasMultipleStops() && route.stopsIncludeOrigin) {
            throw new IllegalArgumentException("Başlangıç koordinatı geçersiz.");
        }
        payload.put("arrivalPoint", buildJourneyPoint(arrival));
        return payload;
    }

    private static JSONObject buildJourneyPoint(GezRouteBridge.GezPoint point) throws JSONException {
        if (point == null || !validJourneyCoordinates(point.lat, point.lon)) {
            throw new IllegalArgumentException("Rota koordinatı geçersiz.");
        }
        JSONObject coordinates = new JSONObject().put("latitude", point.lat).put("longitude", point.lon);
        String label = point.name == null || point.name.trim().isEmpty() ? "Hedef" : point.name.trim();
        return new JSONObject().put("geoLocation", coordinates).put("address", new JSONObject().put("label", label));
    }

    private static boolean validJourneyCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
                && (lat != 0 || lon != 0);
    }

    public void fetchTelemetry(TelemetryCallback callback) {
        new Thread(() -> {
            if (storage.isDemoMode()) {
                VehicleTelemetry demo = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
                callback.onSuccess(demo, false);
                return;
            }
            if (!storage.hasValidToken()) {
                callback.onError("Canlı araç verisi için geçerli Togg ID oturumu gereklidir.", storage.getActiveTelemetry());
                return;
            }

            // If user has a valid token but hasn't entered their real vehicle VIN yet:
            if (storage.isDemoVin()) {
                VehicleTelemetry fallback = storage.getActiveTelemetry();
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
                if (parsed == null) {
                    callback.onError("Togg sunucusundan geçerli telemetri alınamadı.", storage.getActiveTelemetry());
                    return;
                }
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
            if (code == 401 && isPermissionError) {
                // If primary endpoint denied, try FriendVehicleInfo before giving up
                if (strategy == STRATEGY_PRIMARY_VIN && !storage.getOwnerId().isEmpty()) {
                    executeFetch(callback, allowTokenRefresh, STRATEGY_FRIEND_VIN);
                    return;
                }

                String msg = "Şasi Numarası Yetki Hatası (HTTP 401): Togg sunucusu hesabınızın '" + vin + "' şasi numaralı araca erişim izni olmadığını bildirdi.\n\n"
                        + "Lütfen Trumore uygulamasındaki 'Akıllı Cihazım' sekmesinden veya ruhsatınızdan 17 haneli şasi numaranızı (VIN) kontrol edip giriniz.";
                if (!errorDetail.isEmpty() && errorDetail.length() < 250) {
                    msg += "\nDetay: " + errorDetail;
                }
                VehicleTelemetry fallback = storage.getActiveTelemetry();
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
                        VehicleTelemetry fallback = storage.getActiveTelemetry();
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

            VehicleTelemetry fallback = storage.getActiveTelemetry();
            callback.onError(msg, fallback);

        } catch (Exception e) {
            VehicleTelemetry fallback = storage.getActiveTelemetry();
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
