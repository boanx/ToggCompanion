package com.togg.companion.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.togg.companion.core.SmartAccessCertificate;
import com.togg.companion.core.SmartAccessKeyStore;
import com.togg.companion.data.SecureTokenStorage;
import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** Certificate cache is scoped to this app and accepts only authenticated server responses. */
public final class SmartAccessCertificateClient {
    static final String API_URL = "https://tva-api.togg.cloud/smart-access/api/v1";
    static final String BASE_URL = API_URL + "/Certificates";
    public interface Callback {
        void onSuccess(SmartAccessCertificate certificate);
        void onError(String message);
        default void onDiagnostic(String event) { }
    }

    private final SecureTokenStorage storage;
    private final SmartAccessKeyStore keyStore;
    private final SharedPreferences preferences;

    public SmartAccessCertificateClient(Context context, SecureTokenStorage storage) {
        if (context == null || storage == null) throw new IllegalArgumentException("Smart Access yapılandırması eksik.");
        this.storage = storage;
        this.keyStore = new SmartAccessKeyStore(context);
        SharedPreferences encryptedCache = null;
        try {
            Context app = context.getApplicationContext();
            MasterKey key = new MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            encryptedCache = EncryptedSharedPreferences.create(app, "togg_smart_access_certificate_cache_v1", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception error) {
            // Keep the screen usable; setup reports the unavailable secure cache without falling back.
        }
        preferences = encryptedCache;
    }

    public SmartAccessCertificate getCached(String truId, String vin) {
        try {
            if (preferences == null || storage.isDemoMode()) return null;
            String normalizedUser = SmartAccessKeyStore.normalizeTruId(truId);
            String normalizedVin = SmartAccessKeyStore.normalizeVin(vin);
            if (storage.hasValidToken() && !normalizedUser.equals(accountTruId(storage.getAccessToken()))) return null;
            String stored = preferences.getString(cacheKey(normalizedUser, normalizedVin), "");
            if (stored.isEmpty()) return null;
            return SmartAccessCertificate.parseFromServer(new JSONObject(stored), normalizedVin, normalizedUser,
                    keyStore.getPublicKey(normalizedUser, normalizedVin));
        } catch (Exception error) { return null; }
    }

    /** Original Trumore obtains its user UUID from the authenticated access-token subject. */
    public String getSuggestedTruId(String vin) {
        try {
            if (preferences == null || storage.isDemoMode()) return "";
            if (storage.hasValidToken()) return accountTruId(storage.getAccessToken());
            String normalizedVin = SmartAccessKeyStore.normalizeVin(vin);
            String identity = preferences.getString("verified_user_" + normalizedVin, "");
            return identity.isEmpty() ? "" : SmartAccessKeyStore.normalizeTruId(identity);
        } catch (Exception error) { return ""; }
    }

    public void fetch(String truId, String vin, boolean isOwner, Callback callback) {
        if (callback == null) return;
        new Thread(() -> {
            try {
                String user = SmartAccessKeyStore.normalizeTruId(truId);
                String vehicle = SmartAccessKeyStore.normalizeVin(vin);
                requireOwnerSession(user, vehicle);
                keyStore.ensureKey(user, vehicle);
                checkPhoneRelation(user, vehicle, callback);
                fetchAuthorized(user, vehicle, isOwner, callback);
            } catch (Exception error) { callback.onError(safeError(error)); }
        }, "togg-smart-access-certificate").start();
    }

    public void createOwner(String truId, String vin, Callback callback) {
        if (callback == null) return;
        new Thread(() -> {
            try {
                String user = SmartAccessKeyStore.normalizeTruId(truId);
                String vehicle = SmartAccessKeyStore.normalizeVin(vin);
                requireOwnerSession(user, vehicle);
                if (!storage.getOwnerId().isEmpty()) throw new IllegalArgumentException("Paylaşılan araç anahtarı araç sahibi tarafından Trumore'dan etkinleştirilmelidir.");
                keyStore.ensureKey(user, vehicle);
                checkPhoneRelation(user, vehicle, callback);
                claimOwnerPhone(user, vehicle, callback);
                createAuthorizedOwner(user, vehicle, callback);
            } catch (Exception error) { callback.onError(safeError(error)); }
        }, "togg-smart-access-certificate-create").start();
    }

    private void requireOwnerSession(String truId, String vin) {
        if (preferences == null || !storage.isEncryptedStorageActive()) throw new IllegalStateException("Smart Access sertifikaları güvenli depolama olmadan kullanılamaz.");
        if (storage.isDemoMode() || !storage.hasValidToken()) throw new IllegalArgumentException("Smart Access kurulumu için geçerli Togg ID oturumu gerekli.");
        if (!truId.equals(accountTruId(storage.getAccessToken()))) throw new IllegalArgumentException("Smart Access kullanıcı kimliği Togg ID oturumuyla eşleşmiyor.");
        if (!vin.equals(SmartAccessKeyStore.normalizeVin(storage.getVin()))) throw new IllegalArgumentException("Smart Access VIN numarası seçili araçla eşleşmiyor.");
    }

    private void fetchAuthorized(String truId, String vin, boolean isOwner, Callback callback) throws Exception {
        byte[] publicKey = keyStore.getPublicKey(truId, vin);
        JSONObject response = request(truId, vin, "GET", buildFetchUrl(publicKey, vin, isOwner), null, callback);
        requireOwnerSession(truId, vin);
        SmartAccessCertificate certificate;
        try { certificate = SmartAccessCertificate.parseFromServer(response, vin, truId, publicKey); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Smart Access [GET_CERTIFICATE] yanıtı: " + error.getMessage());
        }
        if (certificate.isOwner != isOwner) throw new IllegalArgumentException("Sertifika sahiplik türü seçilen erişimle eşleşmiyor.");
        saveServerCertificate(certificate);
        callback.onSuccess(certificate);
    }

    private void checkPhoneRelation(String truId, String vin, Callback callback) throws Exception {
        // Original 21f3bfc -> 21f3ebc runs this before creating/fetching certificates.
        // No Trumore push token is imported; original nullable-token fallback is empty.
        request(truId, vin, "POST", buildPhoneRelationUrl(keyStore.getPublicKey(truId, vin)),
                buildPhoneRelationBody(Locale.getDefault().getLanguage()), callback);
        requireOwnerSession(truId, vin);
    }

    static String buildPhoneRelationUrl(byte[] key) throws Exception {
        return API_URL + "/UserPhoneRelation/CheckUserPhoneRelation?publicKey=" + encode(publicKeyHex(key));
    }
    static JSONObject buildPhoneRelationBody(String language) throws Exception {
        String value = language == null ? "" : language;
        return new JSONObject().put("Platform", new JSONArray().put("android"))
                .put("Language", new JSONArray().put(value))
                .put("PushNotificationToken", new JSONArray().put(""));
    }

    private void claimOwnerPhone(String truId, String vin, Callback callback) throws Exception {
        // Original 21f10d0 -> 21f1728 precedes owner certificate creation.
        // Its three fields occupy the query slot; POST body is an empty map.
        JSONObject response = request(truId, vin, "POST",
                buildClaimOwnerUrl(keyStore.getPublicKey(truId, vin), vin), new JSONObject(), callback);
        requireOwnerSession(truId, vin);
        requireClaimSuccess(response);
    }

    static String buildClaimOwnerUrl(byte[] key, String vin) throws Exception {
        return API_URL + "/VehicleOwnerRelation/ClaimCarToUser?ownerPublicKey=" + encode(publicKeyHex(key))
                + "&vin=" + encode(SmartAccessKeyStore.normalizeVin(vin)) + "&overwritePhoneRelation=false";
    }

    static void requireClaimSuccess(JSONObject response) {
        Object result = response.opt("resultEnum");
        if ("Success".equals(result) || "Updated".equals(result) || "RelationAlreadyExists".equals(result)) return;
        if ("OverwriteDetection".equals(result)) {
            throw new IllegalStateException("Smart Access [CLAIM_OWNER] [OWNER_PHONE_CONFLICT] Araç sahibi için başka bir telefon anahtarı kayıtlı. Mevcut anahtar değiştirilmedi; sertifika oluşturma durduruldu.");
        }
        throw new IllegalStateException("Smart Access [CLAIM_OWNER] [OWNER_RELATION_REJECTED] Sunucu bu telefon anahtarının araç sahibi ilişkisini doğrulamadı; sertifika oluşturma durduruldu.");
    }

    private void createAuthorizedOwner(String truId, String vin, Callback callback) throws Exception {
        byte[] publicKey = keyStore.getPublicKey(truId, vin);
        byte[] signingData = SmartAccessCertificate.buildOwnerSigningData(truId, vin, publicKey, System.currentTimeMillis());
        byte[] shortCertificate = SmartAccessCertificate.appendOwnerSignature(signingData, keyStore.sign(truId, vin, signingData));
        request(truId, vin, "POST", buildCreateUrl(publicKey), buildCreateBody(vin, shortCertificate), callback);
        requireOwnerSession(truId, vin);
        try { fetchAuthorized(truId, vin, true, callback); }
        catch (IllegalStateException error) {
            throw new IllegalStateException("Oluşturma isteği sunucu tarafından kabul edildi. Sertifika henüz alınamadı; biraz sonra 'Sertifikayı al ve yetkilendir' ile tekrar deneyin. " + error.getMessage());
        }
    }

    // Original b95474/b95524 serializes every public-key byte as two hex digits.
    static String publicKeyHex(byte[] key) {
        byte[] normalized = SmartAccessKeyStore.normalizePublicKey(key);
        StringBuilder text = new StringBuilder(130);
        for (byte value : normalized) text.append(String.format(Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
    static String buildFetchUrl(byte[] key, String vin, boolean owner) throws Exception {
        return BASE_URL + "/GetCertificate?phonePublicKey=" + encode(publicKeyHex(key))
                + "&vin=" + encode(SmartAccessKeyStore.normalizeVin(vin)) + "&isOwner=" + owner;
    }
    static String buildCreateUrl(byte[] key) throws Exception {
        return BASE_URL + "/CreateCertificate?phonePublicKey=" + encode(publicKeyHex(key));
    }
    static JSONObject buildCreateBody(String vin, byte[] shortCertificate) throws Exception {
        if (shortCertificate == null || shortCertificate.length != 183 || shortCertificate[0] != 1)
            throw new IllegalArgumentException("Kısa sertifika biçimi geçersiz.");
        return new JSONObject().put("vin", SmartAccessKeyStore.normalizeVin(vin))
                .put("shortCertificateBase64", Base64.getEncoder().encodeToString(shortCertificate))
                .put("isOwnerCertificate", true);
    }
    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private JSONObject request(String truId, String vin, String method, String url, JSONObject body, Callback callback) throws Exception {
        String operation = url.contains("/UserPhoneRelation/") ? "PHONE_RELATION"
                : url.contains("/VehicleOwnerRelation/") ? "CLAIM_OWNER"
                : url.contains("/CreateCertificate") ? "CREATE_CERTIFICATE" : "GET_CERTIFICATE";
        requireOwnerSession(truId, vin);
        String token = storage.getAccessToken();
        if (!truId.equals(accountTruId(token))) throw new IllegalStateException("Togg ID oturumu değişti; kurulumu yeniden başlatın.");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("x-app-build-number", "661");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            callback.onDiagnostic("CERT_HTTP " + operation + " status=" + code);
            if (code < 200 || code >= 300) {
                callback.onDiagnostic("CERT_ERROR_SCHEMA " + operation + " " + errorResponseSchema(connection));
                boolean phoneRelation = url.contains("/UserPhoneRelation/");
                String operationLabel = phoneRelation ? "telefon kaydı"
                        : "CLAIM_OWNER".equals(operation) ? "sahip-telefon kaydı" : "sertifika";
                String advice = code == 401 ? "Togg ID oturumunu yenileyin."
                        : code == 403 ? "Bu hesap veya telefon anahtarı için araç erişimi sunucu tarafından reddedildi."
                        : code == 404 ? phoneRelation ? "Telefon-hesap ilişkisi servisi bulunamadı."
                            : "Bu telefon anahtarına ait sertifika bulunamadı; araç sahibiyseniz yeni sertifika oluşturun."
                        : "Sertifika servisi isteği tamamlayamadı.";
                throw new IllegalStateException("Smart Access [" + operation + "] " + operationLabel
                        + " HTTP " + code + ". " + advice);
            }
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (response.size() + size > 65536) throw new IllegalStateException("Smart Access [" + operation + "] [RESPONSE_SIZE] Yanıt boyut sınırını aştı.");
                    response.write(buffer, 0, size);
                }
            }
            String text = new String(response.toByteArray(), StandardCharsets.UTF_8);
            if (text.trim().isEmpty() && "POST".equals(method)) return new JSONObject();
            try {
                JSONObject result = new JSONObject(text);
                callback.onDiagnostic("CERT_SCHEMA " + operation + " " + responseSchema(result));
                return result;
            }
            catch (org.json.JSONException error) { throw new IllegalStateException("Smart Access [" + operation + "] [RESPONSE_JSON] JSON nesnesi okunamadı (HTTP " + code + ")."); }
        } catch (java.net.SocketTimeoutException error) {
            throw new IllegalStateException("Smart Access [" + operation + "] [NETWORK_TIMEOUT] İstek zaman aşımına uğradı; sonuç doğrulanamadı.");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Smart Access [" + operation + "] [NETWORK_IO] Ağ/TLS bağlantısı tamamlanamadı; sonuç doğrulanamadı.");
        } finally { connection.disconnect(); }
    }

    /** Schema names/types only; no field values or certificate material are emitted. */
    static String responseSchema(JSONObject response) {
        return responseSchema(response, true);
    }

    private static String responseSchema(JSONObject response, boolean exposeAdditionalNames) {
        StringBuilder result = new StringBuilder("root=").append(objectSchema(response, exposeAdditionalNames));
        for (String wrapper : new String[]{"data", "result", "response"}) {
            JSONObject nested = response.optJSONObject(wrapper);
            if (nested != null) result.append(" ").append(wrapper).append("=").append(objectSchema(nested, exposeAdditionalNames));
        }
        return result.toString();
    }

    /** Error bodies can echo requests or exception text: emit fixed field names/types only. */
    private static String errorResponseSchema(HttpURLConnection connection) {
        try (InputStream input = connection.getErrorStream()) {
            if (input == null) return "body=empty";
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int size;
            while ((size = input.read(buffer)) != -1) {
                if (response.size() + size > 8192) return "body=over_limit";
                response.write(buffer, 0, size);
            }
            String text = new String(response.toByteArray(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return "body=empty";
            try {
                JSONObject result = new JSONObject(text);
                String schema = responseSchema(result, false);
                JSONObject errors = result.optJSONObject("errors");
                if (errors != null) schema += " errors=" + objectSchema(errors, false);
                return schema;
            }
            catch (org.json.JSONException ignored) { return "body=non_object_json_or_text"; }
        } catch (java.io.IOException ignored) {
            // Keep the original HTTP status as the primary error if its body cannot be read.
            return "body=unreadable";
        }
    }

    private static String objectSchema(JSONObject value, boolean exposeAdditionalNames) {
        StringBuilder text = new StringBuilder("{");
        int known = 0;
        String[] names = {"vin", "certificateBase64", "timestamp", "data", "result", "response",
                "status", "statusCode", "resultEnum", "resultIndex", "code", "success", "message", "error", "errors",
                "certificate", "certificates", "CertificateBase64", "Vin",
                "type", "title", "detail", "instance", "traceId", "timeToWaitBeforeFetch",
                "phonePublicKey", "publicKey", "shortCertificateBase64", "isOwnerCertificate",
                "Platform", "Language", "PushNotificationToken"};
        for (String name : names) {
            if (!value.has(name)) continue;
            if (known++ > 0) text.append(',');
            Object field = value.opt(name);
            String type = value.isNull(name) ? "null" : field instanceof JSONObject ? "object"
                    : field instanceof JSONArray ? "array" : field instanceof String ? "string"
                    : field instanceof Boolean ? "boolean" : field instanceof Number ? "number" : "other";
            text.append(name).append(':').append(type);
            // Only known status words are copied; arbitrary server text is never logged.
            if (("status".equals(name) || "statusCode".equals(name) || "resultEnum".equals(name)) && field instanceof String) {
                for (String status : new String[]{"Success", "UpToDate", "NotFound", "Error", "Pending",
                        "Unknown", "AccessDenied", "Failure", "NotOwner", "InvalidCertificate",
                        "Updated", "OwnerChange", "RelationAlreadyExists", "OverwriteDetection", "Expired"}) {
                    if (status.equalsIgnoreCase((String) field)) { text.append('(').append(status).append(')'); break; }
                }
            }
            if ("resultIndex".equals(name) && field instanceof Number) {
                int index = ((Number) field).intValue();
                if (index >= 0 && index <= 11 && ((Number) field).doubleValue() == index)
                    text.append('(').append(index).append(')');
            }
        }
        java.util.Set<String> fixedNames = new java.util.HashSet<>(java.util.Arrays.asList(names));
        java.util.List<String> additional = new java.util.ArrayList<>();
        java.util.Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!fixedNames.contains(key)) additional.add(key);
        }
        java.util.Collections.sort(additional);
        int exposed = 0;
        for (String name : additional) {
            if (!exposeAdditionalNames) break;
            // Restrict to short schema identifiers. Redact VIN-shaped/dynamic opaque names.
            if (exposed >= 16) break;
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,47}")
                    || name.matches("[A-HJ-NPR-Z0-9]{17}")
                    || name.matches("[A-Fa-f0-9]{16,48}")) continue;
            Object field = value.opt(name);
            String type = value.isNull(name) ? "null" : field instanceof JSONObject ? "object"
                    : field instanceof JSONArray ? "array" : field instanceof String ? "string"
                    : field instanceof Boolean ? "boolean" : field instanceof Number ? "number" : "other";
            if (known + exposed > 0) text.append(',');
            text.append(name).append(':').append(type);
            exposed++;
        }
        return text.append(";unlistedFields=").append(Math.max(0, additional.size() - exposed)).append('}').toString();
    }

    private void saveServerCertificate(SmartAccessCertificate certificate) {
        if (preferences == null) throw new IllegalStateException("Smart Access sertifikaları güvenli depolama olmadan kullanılamaz.");
        if (!preferences.edit().putString(cacheKey(certificate.truId, certificate.vin), certificate.toServerJson().toString())
                .putString("verified_user_" + certificate.vin, certificate.truId).commit()) {
            throw new IllegalStateException("Smart Access sertifikası güvenli biçimde kaydedilemedi.");
        }
    }

    private static String cacheKey(String user, String vin) { return "server_certificate_" + user + "_" + vin; }

    static String accountTruId(String accessToken) {
        try {
            String[] parts = accessToken == null ? new String[0] : accessToken.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException();
            JSONObject claims = new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return SmartAccessKeyStore.normalizeTruId(claims.getString("sub"));
        } catch (Exception error) { throw new IllegalArgumentException("Togg ID oturumunda geçerli kullanıcı kimliği bulunamadı."); }
    }

    private static String safeError(Exception error) {
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) return error.getMessage();
        return "Smart Access anahtarı veya sertifikası hazırlanamadı. Güvenli depolamayı ve Togg ID oturumunu kontrol edin.";
    }
}
