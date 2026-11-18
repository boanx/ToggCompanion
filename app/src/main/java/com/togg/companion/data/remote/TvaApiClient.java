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
        default void onAccepted(String message) { onSuccess(message); }
        default void onUncertain(String message) { onError(message); }
    }
    // Trumore prod NAVIGATION_API_URL. SUPERAPP_URL is not the navigation API.
    public static final String COMPANIONS_ENDPOINT = "https://navigation-api.togg.cloud/c/api/Companions";
    // Retained for planned-route JSON compatibility; this is not the vehicle sharing service.
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
            @Override public void onAccepted(String message) {
                if (completed.compareAndSet(false, true)) {
                    DESTINATION_IN_FLIGHT.set(false); callback.onAccepted(message);
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
        }, "togg-shared-route").start();
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
            JSONObject payload = buildCompanionSharePayload(
                    route == null ? new GezRouteBridge.GezPoint(lat, lon, name) : route, origin, storage.getRouteClientId());
            if (!sameDestinationSession(vin, requestToken)) {
                callback.onError("Hesap veya araç değişti. Rota gönderimini yeniden başlatın."); return;
            }
            int code = 0;
            String responseText = "", contentType = "";
            // Original share action clears the account's prior shared route, then saves
            // the replacement. Never delete PlannedJourneys or retry an uncertain POST.
            for (String method : new String[]{"DELETE", "POST"}) {
                if (!sameDestinationSession(vin, requestToken)) {
                    callback.onUncertain("Hesap veya araç değişti; paylaşım tamamlanamadı. Araç ekranını kontrol edin."); return;
                }
                connection = (HttpURLConnection) new URL(COMPANIONS_ENDPOINT).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + requestToken);
                if ("POST".equals(method)) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(body.length);
                    try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                }
                code = connection.getResponseCode();
                contentType = connection.getContentType(); responseText = "";
                InputStream response = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                if (response != null) try (InputStream stream = response) { responseText = readDestinationResponse(stream); }
                if (code < 200 || code >= 300) break;
                String responseError = destinationResponseError(responseText, contentType, method);
                if (responseError != null) {
                    String phase = "DELETE".equals(method) ? "Önceki paylaşımı temizleme" : "Yeni rotayı kaydetme";
                    if ("POST".equals(method) && "false".equals(responseText.trim())) {
                        String readbackError = verifySharedRoute(payload, vin, requestToken);
                        if (!sameDestinationSession(vin, requestToken)) callback.onUncertain("Hesap veya araç değişti; araç ekranını kontrol edin.");
                        else reportAcceptedDestinationResponse(responseText, contentType, readbackError, callback);
                        return;
                    }
                    callback.onUncertain(phase + " (" + method + ", HTTP " + code + "): " + responseError
                            + ("DELETE".equals(method) ? " Yeni rota gönderilmedi." : " Otomatik tekrar gönderim yapılmadı; araç ekranını kontrol edin.")); return;
                }
                if ("DELETE".equals(method)) { connection.disconnect(); connection = null; }
            }
            if (!sameDestinationSession(vin, requestToken)) {
                callback.onUncertain("Gönderim sırasında hesap veya araç değişti. Sonuç bu araç için doğrulanamadı; araç ekranını kontrol edin."); return;
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
                String verificationError = verifySharedRoute(payload, vin, requestToken);
                if (!sameDestinationSession(vin, requestToken)) {
                    callback.onUncertain("Hesap veya araç değişti; araç ekranını kontrol edin."); return;
                }
                reportAcceptedDestinationResponse(responseText, contentType, verificationError, callback);
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

    /** POST was HTTP 2xx; a separate GET failure must not turn acceptance into rejection. */
    static void reportAcceptedDestinationResponse(String body, String type, String readbackError, DestinationCallback callback) {
        if (readbackError == null) {
            callback.onSuccess("Rota kaydı sunucudan geri okunarak doğrulandı. Araç ekranını kontrol edin.");
        } else if (destinationResponseError(body, type) == null) {
            callback.onAccepted("Gönderim sunucu tarafından kabul edildi. Geri okuma ayrıntısı: " + readbackError);
        } else {
            callback.onUncertain("Gönderim sonucu kesinleşmedi. " + readbackError + " Hedef ulaşmış olabilir; araç ekranını kontrol edin.");
        }
    }

    private static String readDestinationResponse(InputStream stream) throws Exception {
        InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(); char[] buffer = new char[2048]; int count;
        while ((count = reader.read(buffer)) != -1) {
            if (result.length() + count > 65536) throw new java.io.IOException("Route response limit");
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private String verifySharedRoute(JSONObject expected, String vin, String token) {
        HttpURLConnection readback = null;
        try {
            if (!sameDestinationSession(vin, token)) return "hesap veya araç değişti; kayıt doğrulanamadı.";
            // Original share completion 0x1fce630 reads Companions via GET.
            // A failed read must never replay DELETE/POST or replace another route.
            readback = (HttpURLConnection) new URL(COMPANIONS_ENDPOINT).openConnection();
            readback.setInstanceFollowRedirects(false);
            readback.setRequestMethod("GET");
            readback.setConnectTimeout(15000); readback.setReadTimeout(15000);
            readback.setUseCaches(false);
            readback.setRequestProperty("Cache-Control", "no-cache");
            readback.setRequestProperty("Accept", "application/json");
            readback.setRequestProperty("Authorization", "Bearer " + token);
            int status = readback.getResponseCode();
            if (status < 200 || status >= 300) return "kayıt geri okunamadı (GET, HTTP " + status + ").";
            String body;
            try (InputStream stream = readback.getInputStream()) { body = readDestinationResponse(stream); }
            if (!sameDestinationSession(vin, token)) return "hesap veya araç değişti; kayıt doğrulanamadı.";
            String error = destinationResponseError(body, readback.getContentType());
            return error != null ? "kayıt geri okuma (GET, HTTP " + status + "): " + error
                    : sharedRouteReadbackError(body, expected);
        } catch (Exception unavailable) {
            return "kayıt geri okuma tamamlanamadı (GET); sunucudaki hedef doğrulanamadı.";
        } finally { if (readback != null) readback.disconnect(); }
    }

    static String sharedRouteReadbackError(String body, JSONObject expected) {
        try {
            Object value = new org.json.JSONTokener(body == null ? "" : body).nextValue();
            if (!(value instanceof JSONObject)) return "geri okunan hedef nesnesi eksik (GET).";
            JSONObject actual = (JSONObject) value;
            if (!expected.getString("device_Id").equals(actual.optString("device_Id", "")))
                return "geri okunan kaydın istemci kimliği eşleşmiyor (GET: device_Id).";
            for (String key : new String[]{"startingPoint", "arrivalPoint"}) {
                if (expected.has(key)) {
                    String error = sharedPointReadbackError(actual.optJSONObject(key), expected.getJSONObject(key), key);
                    if (error != null) return error;
                }
            }
            JSONArray wanted = expected.optJSONArray("waypoints"), saved = actual.optJSONArray("waypoints");
            if (saved == null) return "geri okunan ara durak listesi eksik (GET: waypoints).";
            int wantedCount = wanted == null ? 0 : wanted.length(), savedCount = saved == null ? 0 : saved.length();
            if (wantedCount != savedCount) return "geri okunan ara durak sayısı eşleşmiyor (GET: waypoints).";
            for (int i = 0; i < wantedCount; i++) {
                String error = sharedPointReadbackError(saved.optJSONObject(i), wanted.getJSONObject(i), "waypoints[" + i + "]");
                if (error != null) return error;
            }
            return null;
        } catch (JSONException invalid) { return "geri okunan rota şeması çözümlenemedi (GET)."; }
    }

    private static String sharedPointReadbackError(JSONObject actual, JSONObject expected, String key) throws JSONException {
        if (actual == null) return "geri okunan hedef alanı eksik (GET: " + key + ").";
        JSONObject geo = actual.optJSONObject("geolocation"), wanted = expected.getJSONObject("geolocation");
        if (geo == null) return "geri okunan koordinat alanı eksik (GET: " + key + ".geolocation).";
        for (String axis : new String[]{"latitude", "longitude"}) {
            double coordinate = geo.optDouble(axis, Double.NaN);
            if (!Double.isFinite(coordinate) || Math.abs(coordinate - wanted.getDouble(axis)) > 0.00001)
                return "geri okunan koordinat eşleşmiyor (GET: " + key + ".geolocation." + axis + ").";
        }
        JSONObject address = actual.optJSONObject("address");
        if (address == null || address.optString("label", "").trim().isEmpty())
            return "geri okunan adres etiketi eksik (GET: " + key + ".address.label).";
        return null;
    }

    static String destinationResponseError(String body, String contentType) {
        return destinationResponseError(body, contentType, "POST");
    }

    static String destinationResponseError(String body, String contentType, String method) {
        String text = body == null ? "" : body.trim();
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (type.contains("html") || text.startsWith("<"))
            return "Sunucu paylaşım yanıtı yerine web sayfası döndürdü; rota kaydı doğrulanamadı.";
        // Original Companions POST has no typed response parser; an empty 2xx is valid.
        if (text.isEmpty() || "true".equals(text) || "null".equals(text)) return null;
        // Original delete wrapper 0x1fe80ac discards the returned value after
        // awaiting HTTP; dispatch 0x1fce4e8 then proceeds to save unconditionally.
        // A scalar false from a successful DELETE must not suppress the new POST.
        // Keep POST false uncertain: it does not establish a saved route.
        if ("false".equals(text)) return "DELETE".equals(method) ? null
                : "Paylaşım servisi false yanıtı döndürdü; rota kaydı doğrulanamadı.";
        try {
            Object value = new org.json.JSONTokener(text).nextValue();
            if (!(value instanceof JSONObject)) return "Paylaşım servisinden beklenmeyen yanıt alındı; rota kaydı doğrulanamadı.";
            JSONObject json = (JSONObject) value;
            for (String key : new String[]{"success", "isSuccess"})
                if (json.has(key) && Boolean.FALSE.equals(json.opt(key))) return "Paylaşım servisi işlemi reddetti.";
            for (String key : new String[]{"error", "errors"}) {
                Object error = json.opt(key);
                if (error != null && error != JSONObject.NULL && !Boolean.FALSE.equals(error)
                        && !"".equals(error) && !"[]".equals(error.toString()) && !"{}".equals(error.toString()))
                    return "Paylaşım servisi hata bildirdi; rota kaydı doğrulanamadı.";
            }
            String status = json.optString("status", "").toLowerCase(java.util.Locale.ROOT);
            if ("error".equals(status) || "failed".equals(status) || "failure".equals(status)
                    || json.optInt("status", 0) >= 400)
                return "Paylaşım servisi işlemi reddetti.";
            return null;
        } catch (JSONException invalid) {
            return "Paylaşım servisi yanıtı okunamadı; rota kaydı doğrulanamadı.";
        }
    }

    static JSONObject buildCompanionSharePayload(GezRouteBridge.GezPoint route,
            GezRouteBridge.GezPoint currentOrigin, String clientId) throws JSONException {
        if (clientId == null || clientId.trim().isEmpty()) throw new IllegalArgumentException("İstemci kimliği gerekli.");
        JSONObject planned = buildPlannedJourneyPayload(route, currentOrigin);
        // Exact original share schema: lowercase geolocation, flat placeName,
        // source marker and phone installation identity. device_Id is not VIN.
        JSONObject shared = new JSONObject().put("source", "trumore-mobile-app").put("device_Id", clientId);
        for (String key : new String[]{"startingPoint", "arrivalPoint"})
            if (planned.has(key)) shared.put(key, buildCompanionPoint(planned.getJSONObject(key)));
        JSONArray points = planned.optJSONArray("waypoints");
        // Original dispatch always allocates this list, including zero stops.
        // Missing/null differs from [] for consumers of the saved JourneyResponse.
        JSONArray converted = new JSONArray();
        if (points != null) {
            for (int i = 0; i < points.length(); i++) converted.put(buildCompanionPoint(points.getJSONObject(i)));
        }
        shared.put("waypoints", converted);
        return shared;
    }

    private static JSONObject buildCompanionPoint(JSONObject point) throws JSONException {
        String label = point.getJSONObject("address").getString("label");
        return new JSONObject().put("geolocation", point.getJSONObject("geoLocation"))
                .put("placeName", label).put("address", new JSONObject().put("label", label));
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
            String path = new URL(new URL(COMPANIONS_ENDPOINT), location).getPath();
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
                storage.logChargingDiagnostic(com.togg.companion.core.ChargingInfo.diagnostic(body, parsed.isCharging, parsed.timestampMillis));
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
