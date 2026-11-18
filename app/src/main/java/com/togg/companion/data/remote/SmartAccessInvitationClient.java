package com.togg.companion.data.remote;

import android.content.Context;
import android.net.Uri;
import com.togg.companion.core.ToggAuthManager;
import com.togg.companion.core.SmartAccessKeyStore;
import com.togg.companion.core.SmartAccessCertificate;
import com.togg.companion.data.SmartAccessAccountStorage;
import com.togg.companion.data.SecureTokenStorage;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.io.OutputStream;

/** Guest invitation and certificate API; never overwrites owner relations or sends vehicle commands. */
public final class SmartAccessInvitationClient {
    private final SmartAccessAccountStorage account;
    private final SmartAccessKeyStore keys;
    private final SecureTokenStorage telemetry;
    public interface Callback { void onPreview(String description); void onError(String message); }
    public interface AccessCallback {
        void onAccepted(String message);
        void onCertificate(SmartAccessCertificate certificate);
        void onError(String message);
    }

    public SmartAccessInvitationClient(Context context) {
        account = new SmartAccessAccountStorage(context);
        keys = new SmartAccessKeyStore(context);
        telemetry = new SecureTokenStorage(context);
    }

    public void accept(String link, String vehicleVin, AccessCallback callback) {
        final String sessionId, vin, mainSubject;
        try {
            sessionId = validateLink(link);
            vin = SmartAccessKeyStore.normalizeVin(vehicleVin);
            mainSubject = SmartAccessAccountStorage.subject(telemetry.getAccessToken());
            requireTelemetry(vin, mainSubject);
            byte[] reference = decode(Uri.parse(link.trim()).getQueryParameter("vin"));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(vin.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(reference, Arrays.copyOf(hash, 4)))
                throw new IllegalArgumentException("Davetin araç referansı seçili araçla eşleşmiyor; davet kabul edilmedi.");
        } catch (Exception error) { callback.onError(safeError(error)); return; }
        account.withAccessToken(new ToggAuthManager.TokenCallback() {
            @Override public void onSuccess(String token, String ignored) {
                new Thread(() -> {
                    try {
                        String user = SmartAccessAccountStorage.subject(token);
                        JSONObject details = details(sessionId, token);
                        keys.ensurePhoneKey(user);
                        byte[] publicKey = keys.getPhonePublicKey(user);
                        requireAccount(user);
                        requireTelemetry(vin, mainSubject);
                        JSONObject relation = request("POST", "/UserPhoneRelation/CheckUserPhoneRelation?publicKey="
                                + encode(SmartAccessCertificateClient.publicKeyHex(publicKey)),
                                SmartAccessCertificateClient.buildPhoneRelationBody(java.util.Locale.getDefault().getLanguage()), token);
                        requireResult(relation, true);
                        requireAccount(user);
                        byte[] proof = proof(sessionId, Uri.parse(link.trim()).getQueryParameter("no"), user, publicKey);
                        byte[] signature = keys.signPhoneKey(user, proof);
                        if (!SmartAccessKeyStore.verify(proof, signature, publicKey))
                            throw new IllegalStateException("Davet imzası yerel açık anahtarla doğrulanamadı.");
                        JSONObject body = new JSONObject().put("sessionId", sessionId)
                                .put("signatureBase64", Base64.getEncoder().encodeToString(signature))
                                .put("phonePublicKey", SmartAccessCertificateClient.publicKeyHex(publicKey))
                                .put("sharedName", details.optString("sharedName", ""))
                                .put("friendName", profileName(token));
                        requireTelemetry(vin, mainSubject);
                        account.beginInvitationReply(sessionId, user);
                        JSONObject response = request("POST", "/ShareProcess/ReplyToSession", body, token);
                        requireAccount(user);
                        boolean accepted = "Success".equals(response.opt("resultEnum"));
                        account.finishInvitationReply(sessionId, user, accepted);
                        requireResult(response, false);
                        callback.onAccepted("Davet kabul isteği sunucuda başarılı. Paylaşılan sertifikayı sorgulayın; araç sahibi tarafında onay gerekebilir.");
                    } catch (Exception error) { callback.onError(safeError(error)); }
                }, "smart-access-invite-accept").start();
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void fetchCertificate(String vehicleVin, AccessCallback callback) {
        final String vin, mainSubject;
        try {
            vin = SmartAccessKeyStore.normalizeVin(vehicleVin);
            mainSubject = SmartAccessAccountStorage.subject(telemetry.getAccessToken());
            requireTelemetry(vin, mainSubject);
        }
        catch (Exception error) { callback.onError(safeError(error)); return; }
        account.withAccessToken(new ToggAuthManager.TokenCallback() {
            @Override public void onSuccess(String token, String ignored) {
                new Thread(() -> {
                    try {
                        String user = SmartAccessAccountStorage.subject(token);
                        // Lookup only: a new key here would lose the accepted invitation's binding.
                        byte[] key = keys.getPhonePublicKey(user);
                        String path = "/Certificates/GetCertificate?phonePublicKey="
                                + encode(SmartAccessCertificateClient.publicKeyHex(key)) + "&vin=" + encode(vin) + "&isOwner=false";
                        JSONObject response = request("GET", path, null, token);
                        requireAccount(user);
                        requireTelemetry(vin, mainSubject);
                        SmartAccessCertificate certificate = SmartAccessCertificate.parseFromServer(response, vin, user, key);
                        if (certificate.isOwner) throw new IllegalStateException("Paylaşılan hesap için sahip sertifikası döndü; kabul edilmedi.");
                        account.saveCertificate(certificate);
                        callback.onCertificate(certificate);
                    } catch (Exception error) { callback.onError(safeError(error)); }
                }, "smart-access-guest-certificate").start();
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    private static byte[] proof(String session, String nonce, String user, byte[] publicKey) throws Exception {
        UUID uuid = UUID.fromString(SmartAccessKeyStore.normalizeTruId(user));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(decode(session));
        bytes.write(ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array());
        bytes.write(SmartAccessKeyStore.normalizePublicKey(publicKey));
        bytes.write(decode(nonce));
        return bytes.toByteArray();
    }

    private static byte[] decode(String value) {
        if (value == null || value.length() > 2048 || !value.matches("[A-Za-z0-9_+/=-]+"))
            throw new IllegalArgumentException("Davet kodlaması okunamadı.");
        try { return Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException ignored) { return Base64.getUrlDecoder().decode(value); }
    }

    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private void requireAccount(String user) {
        if (!user.equals(account.getSubject())) throw new IllegalStateException("Paylaşılan hesap değişti veya oturum süresi doldu.");
    }

    private void requireTelemetry(String vin, String mainSubject) {
        if (telemetry.isDemoMode() || !telemetry.hasValidToken() || !vin.equals(telemetry.getVin())
                || !mainSubject.equals(SmartAccessAccountStorage.subject(telemetry.getAccessToken())))
            throw new IllegalStateException("Seçili araç veya ana hesap değişti; işlemi yeniden başlatın.");
    }

    private static void requireResult(JSONObject result, boolean relation) {
        Object status = result.opt("resultEnum");
        if ("Success".equals(status) || relation && "Updated".equals(status)) return;
        throw new IllegalStateException(relation ? "Sunucu paylaşılan hesabın telefon kaydını onaylamadı."
                : "Sunucu davet kabulünü Success olarak sonuçlandırmadı; araç erişimi etkin değil.");
    }

    private static String profileName(String token) throws Exception {
        JSONObject claims = new JSONObject(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8));
        String name = claims.optString("name", "");
        if (name.trim().isEmpty()) name = (claims.optString("given_name", "") + " " + claims.optString("family_name", "")).trim();
        name = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        return name.isEmpty() ? "Companion" : name.substring(0, Math.min(120, name.length()));
    }

    private static String safeError(Exception error) {
        if (error instanceof IllegalStateException || error instanceof IllegalArgumentException) return error.getMessage();
        return "Paylaşılan anahtar işlemi tamamlanamadı. İstek gönderildiyse sonucu doğrulamadan davet kabulünü tekrarlamayın.";
    }

    public void preview(String link, Callback callback) {
        final String sessionId;
        try { sessionId = validateLink(link); }
        catch (IllegalArgumentException error) { callback.onError(error.getMessage()); return; }
        account.withAccessToken(new ToggAuthManager.TokenCallback() {
            @Override public void onSuccess(String accessToken, String ignored) {
                new Thread(() -> fetchPreview(sessionId, accessToken, callback), "smart-access-invite-preview").start();
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    private static String validateLink(String link) {
        try {
            if (link == null || link.length() > 8192) throw new IllegalArgumentException();
            Uri uri = Uri.parse(link.trim());
            if (!"https".equals(uri.getScheme()) || !"superapp-api.channels.togg.cloud".equals(uri.getHost())
                    || !"/KeySharing".equals(uri.getPath()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            if (uri.getQueryParameters("seId").size() != 1 || uri.getQueryParameters("no").size() != 1)
                throw new IllegalArgumentException();
            String session = uri.getQueryParameter("seId");
            String nonce = uri.getQueryParameter("no");
            for (String value : new String[]{session, nonce}) {
                if (value == null || value.length() > 2048 || !value.matches("[A-Za-z0-9_+/=-]+"))
                    throw new IllegalArgumentException();
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(value); }
                catch (IllegalArgumentException ignored) { bytes = Base64.getUrlDecoder().decode(value); }
                if (bytes.length == 0 || bytes.length > 1024) throw new IllegalArgumentException();
            }
            // vin in a link is untrusted metadata; it cannot select or authorize the vehicle.
            return session;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Tam Trumore dijital anahtar davet bağlantısını yapıştırın.");
        }
    }

    private void fetchPreview(String sessionId, String accessToken, Callback callback) {
        try {
            JSONObject result = details(sessionId, accessToken);
            String description = "Araç sahibi: " + display(result, "ownerName")
                    + "\nAraç modeli: " + display(result, "carModel")
                    + "\nPaylaşım adı: " + display(result, "sharedName")
                    + "\n\nDavet bilgileri sunucudan alındı. Davet henüz kabul edilmedi; araç erişimi etkin değil.";
            callback.onPreview(description);
        } catch (Exception error) { callback.onError(safeError(error)); }
    }

    private JSONObject details(String sessionId, String token) throws Exception {
        JSONObject result = request("GET", "/ShareProcess/sessionDetail?sessionId=" + encode(sessionId), null, token);
        if (!sessionId.equals(result.opt("sessionId")))
            throw new IllegalStateException("Sunucu yanıtı bu davetle eşleşmedi; davet kabul edilmedi.");
        requireAccount(SmartAccessAccountStorage.subject(token));
        return result;
    }

    private JSONObject request(String method, String path, JSONObject body, String accessToken) throws Exception {
        HttpURLConnection connection = null;
        try {
            requireAccount(SmartAccessAccountStorage.subject(accessToken));
            URL url = new URL("https://tva-api.togg.cloud/smart-access/api/v1" + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-app-build-number", "663");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Paylaşılan anahtar sunucusu işlemi tamamlamadı (HTTP " + code + ").");
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[2048]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (response.size() + size > 65536) throw new IllegalStateException("Davet yanıtı boyut sınırını aştı.");
                    response.write(buffer, 0, size);
                }
            }
            return new JSONObject(new String(response.toByteArray(), StandardCharsets.UTF_8));
        }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static String display(JSONObject object, String key) {
        Object value = object.opt(key);
        if (!(value instanceof String)) return "Belirtilmedi";
        String text = ((String) value).replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        return text.isEmpty() ? "Belirtilmedi" : text.substring(0, Math.min(160, text.length()));
    }
}
