package com.togg.companion.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.togg.companion.core.SmartAccessKeyStore;
import com.togg.companion.core.ToggAuthManager;
import com.togg.companion.core.SmartAccessCertificate;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;

/** Separate guest OAuth tokens; never writes telemetry credentials or plaintext fallback. */
public final class SmartAccessAccountStorage {
    private static final Object ACCOUNT_WRITE_LOCK = new Object();
    private final SharedPreferences preferences;

    public SmartAccessAccountStorage(Context context) {
        SharedPreferences selected = null;
        try {
            Context app = context.getApplicationContext();
            MasterKey key = new MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            selected = EncryptedSharedPreferences.create(app, "togg_smart_access_guest_account",
                    key, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception ignored) { /* Guest login must fail if encrypted storage is unavailable. */ }
        preferences = selected;
    }

    public boolean isAvailable() { return preferences != null; }
    public boolean hasAccount() { return isAvailable() && !preferences.getString("subject", "").isEmpty(); }

    public void saveTokens(String accessToken, String refreshToken, String telemetrySubject) {
        if (!isAvailable()) throw new IllegalStateException("Paylaşılan anahtar hesabı için güvenli depolama açılamadı.");
        String subject = subject(accessToken);
        if (!isFresh(accessToken)) throw new IllegalArgumentException("Paylaşılan anahtar hesabının oturumu geçerli değil.");
        if (subject.equals(telemetrySubject)) {
            throw new IllegalArgumentException("Araç sahibi hesabıyla giriş yapıldı. Paylaşılan anahtar için ayrı hesapla giriş yapın.");
        }
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalArgumentException("Paylaşılan anahtar hesabının yenileme anahtarı alınamadı.");
        }
        synchronized (ACCOUNT_WRITE_LOCK) {
            if (!preferences.edit().putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken).putString("subject", subject).commit()) {
                throw new IllegalStateException("Paylaşılan anahtar hesabı güvenli biçimde kaydedilemedi.");
            }
        }
    }

    public boolean hasValidToken() {
        return isAvailable() && isFresh(preferences.getString("access_token", ""));
    }

    public String getSubject() {
        return hasValidToken() ? preferences.getString("subject", "") : "";
    }

    public void beginInvitationReply(String sessionId, String expectedSubject) {
        synchronized (ACCOUNT_WRITE_LOCK) {
            requireSubject(expectedSubject);
            String key = replyKey(sessionId, expectedSubject);
            String state = preferences.getString(key, "");
            if ("pending".equals(state) || "accepted".equals(state))
                throw new IllegalStateException("Bu davet için kabul isteği daha önce gönderildi. Sertifikayı sorgulayın; kabul isteği tekrar gönderilmedi.");
            if (!preferences.edit().putString(key, "pending").commit())
                throw new IllegalStateException("Davet işlem kaydı güvenli biçimde saklanamadı.");
        }
    }

    public void finishInvitationReply(String sessionId, String expectedSubject, boolean accepted) {
        synchronized (ACCOUNT_WRITE_LOCK) {
            requireSubject(expectedSubject);
            if (!preferences.edit().putString(replyKey(sessionId, expectedSubject), accepted ? "accepted" : "rejected").commit())
                throw new IllegalStateException("Davet sonucu güvenli biçimde saklanamadı; isteği tekrar göndermeyin.");
        }
    }

    public void saveCertificate(SmartAccessCertificate certificate) {
        synchronized (ACCOUNT_WRITE_LOCK) {
            requireSubject(certificate.truId);
            if (certificate.isOwner) throw new IllegalArgumentException("Paylaşılan hesapta sahip sertifikası kullanılamaz.");
            if (!preferences.edit().putString("certificate_" + certificate.truId + "_" + certificate.vin,
                    certificate.toServerJson().toString()).commit())
                throw new IllegalStateException("Paylaşılan sertifika güvenli biçimde saklanamadı.");
        }
    }

    public SmartAccessCertificate getCertificate(String vin, byte[] publicKey) {
        try {
            String subject = getSubject();
            if (subject.isEmpty()) return null;
            String json = preferences.getString("certificate_" + subject + "_" + SmartAccessKeyStore.normalizeVin(vin), "");
            if (json.isEmpty()) return null;
            SmartAccessCertificate certificate = SmartAccessCertificate.parseFromServer(new JSONObject(json), vin, subject, publicKey);
            return certificate.isOwner ? null : certificate;
        } catch (Exception ignored) { return null; }
    }

    private void requireSubject(String expected) {
        if (!expected.equals(getSubject())) throw new IllegalStateException("Paylaşılan hesap değişti veya oturum süresi doldu.");
    }

    private static String replyKey(String sessionId, String subject) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((subject + "|" + sessionId).getBytes(StandardCharsets.UTF_8));
            return "reply_" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ignored) { throw new IllegalStateException("Davet işlem kimliği hazırlanamadı."); }
    }

    /** Refresh only this guest account; an outstanding refresh cannot replace a newer login. */
    public void withAccessToken(ToggAuthManager.TokenCallback callback) {
        if (!isAvailable()) { callback.onError("Paylaşılan hesap için güvenli depolama açılamadı."); return; }
        if (hasValidToken()) { callback.onSuccess(preferences.getString("access_token", ""), ""); return; }
        String previousRefresh = preferences.getString("refresh_token", "");
        String previousSubject = preferences.getString("subject", "");
        if (previousRefresh.isEmpty() || previousSubject.isEmpty()) {
            callback.onError("Önce paylaşılan anahtar hesabıyla giriş yapın."); return;
        }
        ToggAuthManager.refreshAccessToken(previousRefresh, new ToggAuthManager.TokenCallback() {
            @Override public void onSuccess(String accessToken, String refreshToken) {
                try {
                    synchronized (ACCOUNT_WRITE_LOCK) {
                        if (!previousSubject.equals(subject(accessToken)) || !isFresh(accessToken)
                                || !previousRefresh.equals(preferences.getString("refresh_token", ""))) {
                            throw new IllegalStateException("Paylaşılan hesap oturumu değişti; yeniden deneyin.");
                        }
                        if (!preferences.edit().putString("access_token", accessToken)
                                .putString("refresh_token", refreshToken).commit()) {
                            throw new IllegalStateException("Paylaşılan hesap oturumu kaydedilemedi.");
                        }
                    }
                    callback.onSuccess(accessToken, "");
                } catch (IllegalArgumentException | IllegalStateException error) { callback.onError(error.getMessage()); }
            }
            @Override public void onError(String ignored) {
                callback.onError("Paylaşılan hesap oturumu yenilenemedi; bu hesapla yeniden giriş yapın.");
            }
        });
    }

    /** Only decodes claims returned by the normal OAuth exchange; not a token signature verifier. */
    public static String subject(String token) {
        try { return SmartAccessKeyStore.normalizeTruId(claims(token).getString("sub")); }
        catch (Exception ignored) { throw new IllegalArgumentException("Togg ID oturumundaki kullanıcı kimliği okunamadı."); }
    }

    private static boolean isFresh(String token) {
        try {
            JSONObject claims = claims(token);
            SmartAccessKeyStore.normalizeTruId(claims.getString("sub"));
            return claims.getLong("exp") > System.currentTimeMillis() / 1000L + 30;
        } catch (Exception ignored) { return false; }
    }

    private static JSONObject claims(String token) throws Exception {
        if (token == null || token.length() > 65536) throw new IllegalArgumentException();
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException();
        return new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }
}
