package com.togg.companion.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.json.JSONObject;

/** Configuration only; Togg credentials remain in SecureTokenStorage. */
public final class OdometerExportStore {
    private final SharedPreferences prefs;
    public OdometerExportStore(Context context) { prefs = context.getSharedPreferences("odometer_export", Context.MODE_PRIVATE); }
    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public String uri() { return prefs.getString("uri", ""); }
    public String generation() { return prefs.getString("generation", ""); }
    public String scope() { return prefs.getString("scope", ""); }
    public long first() { return prefs.getLong("first", 0); }
    public long minutes() { return prefs.getLong("minutes", 1440); }
    public long last() { return prefs.getLong("last", 0); }
    public long next() { return prefs.getLong("next", 0); }
    public void next(long value) { prefs.edit().putLong("next", value).apply(); }
    public String status() { return prefs.getString("status", "Henüz gönderilmedi"); }
    public void save(boolean enabled, long first, long minutes, String scope) {
        boolean changed = !scope.equals(scope());
        SharedPreferences.Editor edit = prefs.edit().putBoolean("enabled", enabled).putLong("first", first)
                .putLong("minutes", minutes).putString("scope", scope).putString("generation", UUID.randomUUID().toString());
        if (changed) edit.remove("last").remove("status");
        edit.apply();
    }
    public void destination(String uri) {
        prefs.edit().putString("uri", uri).putBoolean("enabled", false).remove("last").remove("status")
                .putString("generation", UUID.randomUUID().toString()).apply();
    }
    public void result(boolean success, String status) {
        SharedPreferences.Editor edit = prefs.edit().putString("status", status);
        if (success) edit.putLong("last", System.currentTimeMillis());
        edit.apply();
    }
    public static String accountScope(SecureTokenStorage storage) {
        String account = storage.getAccessToken();
        try {
            String[] parts = account.split("\\.");
            if (parts.length == 3) {
                JSONObject claims = new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
                if (!claims.optString("sub").isEmpty()) account = claims.optString("iss") + "|" + claims.getString("sub");
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((storage.getVin() + "|" + account).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception error) { return ""; } // fail closed; never export in an unidentifiable context
    }
}
