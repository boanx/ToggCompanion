package com.togg.companion.wear;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local cache on the watch. Ensures immediate display upon opening without waiting for Bluetooth sync.
 */
public final class WatchDataStore {
    private static final String PREF_NAME = "togg_watch_prefs";
    private static final String ENCRYPTED_PREF_NAME = "togg_watch_encrypted_prefs";
    private static final String TAG = "WatchDataStore";
    private static final String KEY_SOC = "soc";
    private static final String KEY_RANGE = "range_km";
    private static final String KEY_CHARGING = "is_charging";
    private static final String KEY_CHARGE_POWER = "charge_power_kw";
    private static final String KEY_BATT_12V = "batt12v_soc";
    private static final String KEY_BATT_12V_ALERT = "batt12v_alert";
    private static final String KEY_TIRES_NORMAL = "tires_normal";
    private static final String KEY_TIRES_SUMMARY = "tires_summary";
    private static final String KEY_LAT = "latitude";
    private static final String KEY_LON = "longitude";
    private static final String KEY_ADDRESS = "park_address";
    private static final String KEY_TIMESTAMP = "timestamp";

    public interface OnDataChangeListener {
        void onDataChanged(WatchTelemetry telemetry);
    }

    private static final CopyOnWriteArrayList<OnDataChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static void registerListener(OnDataChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(OnDataChangeListener listener) {
        listeners.remove(listener);
    }

    public static void saveTelemetry(Context context, WatchTelemetry t) {
        if (context == null || t == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_SOC, t.soc)
                .putInt(KEY_RANGE, t.rangeKm)
                .putBoolean(KEY_CHARGING, t.isCharging)
                .putFloat(KEY_CHARGE_POWER, (float) t.chargePowerKw)
                .putInt(KEY_BATT_12V, t.batt12vSoc)
                .putBoolean(KEY_BATT_12V_ALERT, t.batt12vAlert)
                .putBoolean(KEY_TIRES_NORMAL, t.tiresNormal)
                .putString(KEY_TIRES_SUMMARY, t.tiresSummary)
                .putFloat(KEY_LAT, (float) t.latitude)
                .putFloat(KEY_LON, (float) t.longitude)
                .putString(KEY_ADDRESS, t.parkAddress)
                .putLong(KEY_TIMESTAMP, t.timestamp)
                .apply();

        for (OnDataChangeListener listener : listeners) {
            try {
                listener.onDataChanged(t);
            } catch (Exception ignored) {}
        }
    }

    public static WatchTelemetry getTelemetry(Context context) {
        if (context == null) return WatchTelemetry.createDefault();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_SOC)) {
            return WatchTelemetry.createDefault();
        }

        return new WatchTelemetry(
                prefs.getInt(KEY_SOC, 0),
                prefs.getInt(KEY_RANGE, 0),
                prefs.getBoolean(KEY_CHARGING, false),
                prefs.getFloat(KEY_CHARGE_POWER, 0.0f),
                prefs.getInt(KEY_BATT_12V, 0),
                prefs.getBoolean(KEY_BATT_12V_ALERT, false),
                prefs.getBoolean(KEY_TIRES_NORMAL, true),
                prefs.getString(KEY_TIRES_SUMMARY, "Henüz senkronize edilmedi"),
                prefs.getFloat(KEY_LAT, 0.0f),
                prefs.getFloat(KEY_LON, 0.0f),
                prefs.getString(KEY_ADDRESS, "Araç verisi telefondan bekleniyor"),
                prefs.getLong(KEY_TIMESTAMP, 0)
        );
    }

    public static boolean hasTelemetry(Context context) {
        return context != null && context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).contains(KEY_SOC);
    }

    // ==========================================
    // WATCH DIGITAL KEY STORAGE
    // ==========================================

    private static final String KEY_DK_ACTIVE = "dk_active";
    private static final String KEY_DK_ID = "dk_id";
    private static final String KEY_DK_SE_ID = "dk_se_id";
    private static final String KEY_DK_VIN = "dk_vin";
    private static final String KEY_DK_PLATE = "dk_plate";
    private static final String KEY_DK_HOLDER = "dk_holder";
    private static final String KEY_DK_VALID_UNTIL = "dk_valid_until";
    private static final String KEY_DK_TOKEN = "dk_token";
    private static final String KEY_DK_CAN_LOCK = "dk_can_lock";
    private static final String KEY_DK_CAN_UNLOCK = "dk_can_unlock";

    private static SharedPreferences keyPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences encrypted = EncryptedSharedPreferences.create(
                    context.getApplicationContext(), ENCRYPTED_PREF_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            SharedPreferences legacy = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (legacy.contains(KEY_DK_TOKEN)) {
                SharedPreferences.Editor editor = encrypted.edit();
                if (!encrypted.contains(KEY_DK_TOKEN)) {
                    editor.putBoolean(KEY_DK_ACTIVE, legacy.getBoolean(KEY_DK_ACTIVE, false))
                            .putString(KEY_DK_ID, legacy.getString(KEY_DK_ID, ""))
                            .putString(KEY_DK_SE_ID, legacy.getString(KEY_DK_SE_ID, ""))
                            .putString(KEY_DK_VIN, legacy.getString(KEY_DK_VIN, ""))
                            .putString(KEY_DK_PLATE, legacy.getString(KEY_DK_PLATE, ""))
                            .putString(KEY_DK_HOLDER, legacy.getString(KEY_DK_HOLDER, ""))
                            .putLong(KEY_DK_VALID_UNTIL, legacy.getLong(KEY_DK_VALID_UNTIL, 0))
                            .putString(KEY_DK_TOKEN, legacy.getString(KEY_DK_TOKEN, ""))
                            .putBoolean(KEY_DK_CAN_LOCK, legacy.getBoolean(KEY_DK_CAN_LOCK, true))
                            .putBoolean(KEY_DK_CAN_UNLOCK, legacy.getBoolean(KEY_DK_CAN_UNLOCK, true));
                    if (!editor.commit()) throw new IllegalStateException("Watch key migration failed");
                }
                if (!legacy.edit().remove(KEY_DK_ACTIVE).remove(KEY_DK_ID).remove(KEY_DK_SE_ID)
                        .remove(KEY_DK_VIN).remove(KEY_DK_PLATE).remove(KEY_DK_HOLDER)
                        .remove(KEY_DK_VALID_UNTIL).remove(KEY_DK_TOKEN)
                        .remove(KEY_DK_CAN_LOCK).remove(KEY_DK_CAN_UNLOCK).commit()) {
                    Log.e(TAG, "SECURITY ALERT: Failed to remove legacy watch digital key");
                }
            }
            return encrypted;
        } catch (Exception error) {
            Log.e(TAG, "SECURITY ALERT: Encrypted watch digital key storage unavailable", error);
            throw new IllegalStateException("Encrypted watch digital key storage unavailable", error);
        }
    }

    public static class WatchDigitalKey {
        public final boolean isActive;
        public final String keyId;
        public final String seId;
        public final String vin;
        public final String plate;
        public final String holder;
        public final long validUntil;
        public final String token;
        public final boolean canLock;
        public final boolean canUnlock;

        public WatchDigitalKey(boolean isActive, String keyId, String seId, String vin, String plate,
                               String holder, long validUntil, String token, boolean canLock, boolean canUnlock) {
            this.isActive = isActive;
            this.keyId = keyId != null ? keyId : "";
            this.seId = seId != null ? seId : "";
            this.vin = vin != null ? vin : "";
            this.plate = plate != null ? plate : "";
            this.holder = holder != null ? holder : "Paylaşılan Kullanıcı";
            this.validUntil = validUntil;
            this.token = token != null ? token : "";
            this.canLock = canLock;
            this.canUnlock = canUnlock;
        }

        public boolean isValid() {
            return isActive && (validUntil <= 0 || System.currentTimeMillis() < validUntil) && !token.isEmpty();
        }

        public String getRemainingDays() {
            if (validUntil <= 0) return "Sınırsız";
            long diff = validUntil - System.currentTimeMillis();
            if (diff <= 0) return "Süresi doldu";
            long days = diff / (24L * 3600 * 1000);
            if (days >= 1) return days + " gün geçerli";
            long hours = diff / (3600 * 1000);
            return Math.max(1, hours) + " saat geçerli";
        }
    }

    public static void saveDigitalKey(Context context, WatchDigitalKey key) {
        if (context == null || key == null) return;
        SharedPreferences prefs = keyPrefs(context);
        boolean saved = prefs.edit()
                .putBoolean(KEY_DK_ACTIVE, key.isActive)
                .putString(KEY_DK_ID, key.keyId)
                .putString(KEY_DK_SE_ID, key.seId)
                .putString(KEY_DK_VIN, key.vin)
                .putString(KEY_DK_PLATE, key.plate)
                .putString(KEY_DK_HOLDER, key.holder)
                .putLong(KEY_DK_VALID_UNTIL, key.validUntil)
                .putString(KEY_DK_TOKEN, key.token)
                .putBoolean(KEY_DK_CAN_LOCK, key.canLock)
                .putBoolean(KEY_DK_CAN_UNLOCK, key.canUnlock)
                .commit();
        if (!saved) throw new IllegalStateException("Encrypted watch digital key save failed");

        WatchTelemetry t = getTelemetry(context);
        for (OnDataChangeListener listener : listeners) {
            try {
                listener.onDataChanged(t);
            } catch (Exception ignored) {}
        }
    }

    public static WatchDigitalKey getDigitalKey(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = keyPrefs(context);
        if (!prefs.getBoolean(KEY_DK_ACTIVE, false)) {
            return null;
        }
        return new WatchDigitalKey(
                prefs.getBoolean(KEY_DK_ACTIVE, false),
                prefs.getString(KEY_DK_ID, ""),
                prefs.getString(KEY_DK_SE_ID, ""),
                prefs.getString(KEY_DK_VIN, ""),
                prefs.getString(KEY_DK_PLATE, ""),
                prefs.getString(KEY_DK_HOLDER, "Paylaşılan Kullanıcı"),
                prefs.getLong(KEY_DK_VALID_UNTIL, 0),
                prefs.getString(KEY_DK_TOKEN, ""),
                prefs.getBoolean(KEY_DK_CAN_LOCK, true),
                prefs.getBoolean(KEY_DK_CAN_UNLOCK, true)
        );
    }

    public static boolean hasDigitalKey(Context context) {
        WatchDigitalKey k = getDigitalKey(context);
        return k != null && k.isValid();
    }

    public static void removeDigitalKey(Context context) {
        if (context == null) return;
        SharedPreferences prefs = keyPrefs(context);
        prefs.edit()
                .clear().commit();

        WatchTelemetry t = getTelemetry(context);
        for (OnDataChangeListener listener : listeners) {
            try {
                listener.onDataChanged(t);
            } catch (Exception ignored) {}
        }
    }
}
