package com.togg.companion.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages Trumore digital key redemption, URL/invitation parsing, persistence,
 * and authorization lifecycle.
 */
public final class DigitalKeyManager {
    private static final String TAG = "DigitalKeyManager";
    private static final String PREF_NAME = "togg_digital_key_prefs";
    private static final String ENCRYPTED_PREF_NAME = "togg_digital_key_prefs_encrypted";
    private static final String KEY_SAVED_JSON = "active_digital_key_json";
    private static final String KEY_ENCRYPTED_MIGRATION_COMPLETE = "__encrypted_migration_complete";

    public interface OnKeyChangeListener {
        void onKeyChanged(DigitalKey activeKey);
    }

    private static final CopyOnWriteArrayList<OnKeyChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final SharedPreferences prefs;
    private final boolean isEncrypted;

    public DigitalKeyManager(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences legacyPrefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences selectedPrefs;
        boolean encrypted = false;
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            selectedPrefs = EncryptedSharedPreferences.create(
                    appContext,
                    ENCRYPTED_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            if (selectedPrefs.getBoolean(KEY_ENCRYPTED_MIGRATION_COMPLETE, false)) {
                if (!legacyPrefs.getAll().isEmpty()) legacyPrefs.edit().clear().commit();
            } else {
                String savedKey = legacyPrefs.getString(KEY_SAVED_JSON, null);
                SharedPreferences.Editor editor = selectedPrefs.edit();
                if (savedKey != null && !selectedPrefs.contains(KEY_SAVED_JSON)) {
                    editor.putString(KEY_SAVED_JSON, savedKey);
                }
                editor.putBoolean(KEY_ENCRYPTED_MIGRATION_COMPLETE, true);
                if (editor.commit()) legacyPrefs.edit().clear().commit();
                else throw new IllegalStateException("Unable to migrate digital key preferences");
            }
            encrypted = true;
        } catch (Throwable error) {
            Log.e(TAG, "SECURITY ALERT: EncryptedSharedPreferences unavailable; MasterKey generation failed.", error);
            selectedPrefs = legacyPrefs;
        }
        this.prefs = selectedPrefs;
        this.isEncrypted = encrypted;
    }

    public boolean isEncryptedStorageActive() { return isEncrypted; }

    public static void registerListener(OnKeyChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(OnKeyChangeListener listener) {
        listeners.remove(listener);
    }

    public synchronized void saveKey(DigitalKey key) {
        if (key == null) return;
        prefs.edit().putString(KEY_SAVED_JSON, key.toJson().toString()).apply();
        notifyListeners(key);
    }

    public synchronized DigitalKey getActiveKey() {
        String jsonStr = prefs.getString(KEY_SAVED_JSON, null);
        if (jsonStr == null || jsonStr.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            return DigitalKey.fromJson(obj);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse saved digital key", e);
            return null;
        }
    }

    public synchronized boolean hasActiveKey() {
        DigitalKey key = getActiveKey();
        return key != null && key.isValid();
    }

    public synchronized void removeKey() {
        prefs.edit().remove(KEY_SAVED_JSON).apply();
        notifyListeners(null);
    }

    private void notifyListeners(DigitalKey key) {
        for (OnKeyChangeListener l : listeners) {
            try {
                l.onKeyChanged(key);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Parses an input string (Trumore deep link, web invitation URL, SMS/WhatsApp share text,
     * reference code, or JSON payload) into a DigitalKey object.
     *
     * @param input Raw string from share-sheet, deep link intent, QR scanner, or manual text box
     * @return Parsed DigitalKey or null if unrecognized
     */
    public static DigitalKey parseFromInput(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;

        // 1. Try parsing as direct JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JSONObject obj = new JSONObject(trimmed);
                return DigitalKey.fromJson(obj);
            } catch (Exception ignored) {}
        }

        // 2. Check if text contains a URL (Trumore deep link or HTTPS link)
        String urlString = extractUrl(trimmed);
        if (urlString != null) {
            Uri parsedUri = null;
            try { parsedUri = Uri.parse(urlString); } catch (Exception ignored) {}
            DigitalKey key = parseFromUri(parsedUri, urlString);
            if (key != null) return key;
        }

        // 3. Try parsing direct URI
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")
                || trimmed.startsWith("trumore-superapp://") || trimmed.startsWith("togg://")) {
            Uri parsedUri = null;
            try { parsedUri = Uri.parse(trimmed); } catch (Exception ignored) {}
            DigitalKey key = parseFromUri(parsedUri, trimmed);
            if (key != null) return key;
        }

        // 4. Try extracting reference code (e.g. "SE-8F4219" or "TOGG-KEY-1234" or "REF: 123456")
        String refCode = extractReferenceCode(trimmed);
        if (refCode != null) {
            return new DigitalKey(
                    "KEY-" + refCode,
                    refCode,
                    "T10F-DEV-001",
                    "34 TOGG 10",
                    "Trumore Referanslı Anahtar (" + refCode + ")",
                    System.currentTimeMillis(),
                    System.currentTimeMillis() + (30L * 24 * 3600 * 1000),
                    true, true, true, true, true,
                    "TRUMORE_REF_TOKEN_" + refCode,
                    true,
                    System.currentTimeMillis()
            );
        }

        // 5. Fallback: If user entered an arbitrary token or code > 6 characters
        if (trimmed.length() >= 6 && !trimmed.contains(" ") && !trimmed.contains("<")) {
            return new DigitalKey(
                    "KEY-MANUAL-" + System.currentTimeMillis(),
                    trimmed,
                    "T10F-DEV-001",
                    "34 TOGG 10",
                    "Özel Tanımlı Anahtar",
                    System.currentTimeMillis(),
                    System.currentTimeMillis() + (30L * 24 * 3600 * 1000),
                    true, true, true, true, true,
                    trimmed,
                    true,
                    System.currentTimeMillis()
            );
        }

        return null;
    }

    /**
     * Parses standard Trumore Key Sharing URIs:
     * - trumore-superapp://custom/redirection?path=/keysharingInvitation&seId=...&token=...&vin=...
     * - https://trumore.app/keysharingInvitation?seId=...&token=...
     * - https://superapp-api.channels.togg.cloud/keysharingInvitation?seId=...
     * - https://.../KeySharing?seId=...
     * - togg://keysharing?seId=...&token=...
     */
    public static DigitalKey parseFromUri(Uri uri) {
        String raw = null;
        if (uri != null) {
            try { raw = uri.toString(); } catch (Exception ignored) {}
        }
        return parseFromUri(uri, raw);
    }

    public static DigitalKey parseFromUri(Uri uri, String rawUrl) {
        if (uri == null && rawUrl == null) return null;

        String seId = getQueryParam(uri, rawUrl, "seId");
        String token = getQueryParam(uri, rawUrl, "token");
        String vin = getQueryParam(uri, rawUrl, "vin");
        String plate = getQueryParam(uri, rawUrl, "plate");
        String recipient = getQueryParam(uri, rawUrl, "name");
        String validUntilStr = getQueryParam(uri, rawUrl, "validUntil");

        // Handle wrapped redirection: trumore-superapp://custom/redirection?target=...
        if (seId == null) {
            String target = getQueryParam(uri, rawUrl, "target");
            if (target != null && !target.isEmpty()) {
                try {
                    String decodedTarget = URLDecoder.decode(target, StandardCharsets.UTF_8.name());
                    Uri nextUri = null;
                    try { nextUri = Uri.parse(decodedTarget); } catch (Exception ignored) {}
                    return parseFromUri(nextUri, decodedTarget);
                } catch (Exception ignored) {}
            }
        }

        // If path itself contains seId or token (e.g. /KeySharing/SE-12345)
        if (seId == null && rawUrl != null) {
            Pattern p = Pattern.compile("(?i)/(?:KeySharing|keysharingInvitation)/([A-Z0-9_-]+)");
            Matcher m = p.matcher(rawUrl);
            if (m.find()) {
                seId = m.group(1);
            }
        }

        if (seId != null || token != null) {
            String resolvedSeId = (seId != null && !seId.isEmpty()) ? seId : "SE-" + System.currentTimeMillis();
            String resolvedToken = (token != null && !token.isEmpty()) ? token : "TOKEN_" + resolvedSeId;
            String resolvedVin = (vin != null && !vin.isEmpty()) ? vin : "T10F-DEV-001";
            String resolvedPlate = (plate != null && !plate.isEmpty()) ? plate : "34 TOGG 10";
            String resolvedRecipient = (recipient != null && !recipient.isEmpty()) ? recipient : "Trumore Paylaşılan Anahtar";

            long validUntil = System.currentTimeMillis() + (30L * 24 * 3600 * 1000);
            if (validUntilStr != null) {
                try {
                    validUntil = Long.parseLong(validUntilStr);
                } catch (NumberFormatException ignored) {}
            }

            return new DigitalKey(
                    "KEY-" + resolvedSeId,
                    resolvedSeId,
                    resolvedVin,
                    resolvedPlate,
                    resolvedRecipient,
                    System.currentTimeMillis(),
                    validUntil,
                    true, true, true, true, true,
                    resolvedToken,
                    true,
                    System.currentTimeMillis()
            );
        }

        return null;
    }

    private static String getQueryParam(Uri uri, String rawUrl, String paramName) {
        if (uri != null) {
            try {
                String val = uri.getQueryParameter(paramName);
                if (val != null) return val;
            } catch (Exception ignored) {}
        }
        if (rawUrl != null) {
            Pattern p = Pattern.compile("[?&]" + Pattern.quote(paramName) + "=([^&#\\s]*)");
            Matcher m = p.matcher(rawUrl);
            if (m.find()) {
                try {
                    return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    private static String extractUrl(String text) {
        Pattern pattern = Pattern.compile("(?i)\\b((?:https?://|trumore-superapp://|togg://)[^\\s<>\"]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractReferenceCode(String text) {
        // Direct SE-XXXXX code (e.g. SE-8F4219, SE-B45901)
        Pattern directSe = Pattern.compile("(?i)\\bSE-([A-Z0-9]{4,12})\\b");
        Matcher mSe = directSe.matcher(text);
        if (mSe.find()) {
            return "SE-" + mSe.group(1).toUpperCase(java.util.Locale.ROOT);
        }

        // Patterns like "kod: XXXXX", "referans: XXXXX", "anahtar: XXXXX"
        Pattern pattern = Pattern.compile("(?i)(?:referans|kod|code|anahtar)[-_:\\s]+(?:se[-_\\s]*)?([A-Z0-9]{4,12})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return "SE-" + matcher.group(1).toUpperCase(java.util.Locale.ROOT);
        }
        return null;
    }
}
