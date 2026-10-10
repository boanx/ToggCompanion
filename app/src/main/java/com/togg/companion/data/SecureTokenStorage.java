package com.togg.companion.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.togg.companion.core.VehicleTelemetry;
import org.json.JSONObject;

import java.util.Map;

/**
 * Encapsulates persistent app settings and authorization tokens.
 */
public final class SecureTokenStorage {
    private static final String PREFS_NAME = "togg_companion_secure_prefs";
    private static final String ENCRYPTED_PREFS_NAME = "togg_companion_secure_prefs_encrypted";
    private static final String KEY_ENCRYPTED_MIGRATION_COMPLETE = "__encrypted_migration_complete";
    private static final String TAG = "SecureTokenStorage";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_VIN = "vehicle_vin";
    private static final String KEY_OWNER_ID = "vehicle_owner_id";
    private static final String KEY_PLATE = "vehicle_plate";
    private static final String KEY_DEMO_MODE = "is_demo_mode";
    private static final String KEY_API_ENDPOINT = "api_endpoint";
    private static final String KEY_LAST_SYNC = "last_sync_millis";
    private static final String KEY_LAST_REFRESH = "key_last_refresh";
    private static final String KEY_ACTIVE_TELEMETRY = "active_telemetry_json";
    private static final String KEY_VEHICLE_CUSTOM_NAME = "vehicle_custom_name";
    private static final String KEY_ABRP_ENABLED = "abrp_enabled";
    private static final String KEY_ABRP_TOKEN = "abrp_token";
    private static final String KEY_ABRP_CAPACITY = "abrp_capacity";
    private static final String KEY_ABRP_LAST_SYNC = "abrp_last_sync";
    private static final String KEY_ABRP_ROUTE_SYNC_ENABLED = "abrp_route_sync_enabled";
    private static final String KEY_ABRP_LAST_DISPATCHED_STOP = "abrp_last_dispatched_stop";
    private static final String KEY_ABRP_LAST_ROUTE_CHECK = "abrp_last_route_check";
    private static final String KEY_ABRP_ACTIVE_STOP_NAME = "abrp_active_stop_name";
    private static final String DEFAULT_VEHICLE_NAME = "Togg T10F Fastback";

    public static final String DEFAULT_API_ENDPOINT = "https://tva-api.togg.cloud/info/api/v2/VehicleInfo";

    private final SharedPreferences prefs;
    private final boolean isEncrypted;

    public SecureTokenStorage(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences legacyPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences selectedPrefs;
        boolean encrypted = false;
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            selectedPrefs = EncryptedSharedPreferences.create(
                    appContext,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            migratePlaintextPreferences(legacyPrefs, selectedPrefs);
            encrypted = true;
        } catch (Throwable error) {
            Log.e(TAG, "SECURITY ALERT: EncryptedSharedPreferences unavailable; MasterKey generation failed.", error);
            selectedPrefs = legacyPrefs;
        }
        this.prefs = selectedPrefs;
        this.isEncrypted = encrypted;
    }

    public boolean isEncryptedStorageActive() { return isEncrypted; }

    public boolean isAbrpEnabled() { return prefs.getBoolean(KEY_ABRP_ENABLED, false); }
    public void setAbrpEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_ABRP_ENABLED, enabled).apply(); }
    public String getAbrpToken() { return prefs.getString(KEY_ABRP_TOKEN, ""); }
    public void setAbrpToken(String token) { prefs.edit().putString(KEY_ABRP_TOKEN, token == null ? "" : token.trim()).apply(); }
    public double getAbrpCapacity() { return Double.longBitsToDouble(prefs.getLong(KEY_ABRP_CAPACITY, Double.doubleToLongBits(88.5))); }
    public void setAbrpCapacity(double capacity) { prefs.edit().putLong(KEY_ABRP_CAPACITY, Double.doubleToLongBits(capacity)).apply(); }
    public long getAbrpLastSync() { return prefs.getLong(KEY_ABRP_LAST_SYNC, 0L); }
    public void setAbrpLastSync(long millis) { prefs.edit().putLong(KEY_ABRP_LAST_SYNC, millis).apply(); }
    public boolean isAbrpRouteSyncEnabled() { return prefs.getBoolean(KEY_ABRP_ROUTE_SYNC_ENABLED, false); }
    public void setAbrpRouteSyncEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_ABRP_ROUTE_SYNC_ENABLED, enabled).apply(); }
    public String getAbrpLastDispatchedStop() { return prefs.getString(KEY_ABRP_LAST_DISPATCHED_STOP, ""); }
    public void setAbrpLastDispatchedStop(String stopKey) { prefs.edit().putString(KEY_ABRP_LAST_DISPATCHED_STOP, stopKey == null ? "" : stopKey).apply(); }
    public long getAbrpLastRouteCheck() { return prefs.getLong(KEY_ABRP_LAST_ROUTE_CHECK, 0L); }
    public void setAbrpLastRouteCheck(long millis) { prefs.edit().putLong(KEY_ABRP_LAST_ROUTE_CHECK, millis).apply(); }
    public String getAbrpActiveStopName() { return prefs.getString(KEY_ABRP_ACTIVE_STOP_NAME, ""); }
    public void setAbrpActiveStopName(String name) { prefs.edit().putString(KEY_ABRP_ACTIVE_STOP_NAME, name == null ? "" : name.trim()).apply(); }

    private static void migratePlaintextPreferences(SharedPreferences legacy, SharedPreferences encrypted) {
        if (encrypted.getBoolean(KEY_ENCRYPTED_MIGRATION_COMPLETE, false)) {
            if (!legacy.getAll().isEmpty()) legacy.edit().clear().commit();
            return;
        }
        Map<String, ?> values = legacy.getAll();
        SharedPreferences.Editor editor = encrypted.edit();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (encrypted.contains(key)) continue;
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof java.util.Set<?>) {
                @SuppressWarnings("unchecked") java.util.Set<String> stringSet = (java.util.Set<String>) value;
                editor.putStringSet(key, stringSet);
            }
        }
        editor.putBoolean(KEY_ENCRYPTED_MIGRATION_COMPLETE, true);
        if (editor.commit()) legacy.edit().clear().commit();
        else throw new IllegalStateException("Unable to migrate private preferences");
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }

    public void setAccessToken(String token) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token != null ? token.trim() : "").apply();
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, "");
    }

    public void setRefreshToken(String token) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token != null ? token.trim() : "").apply();
    }

    public static final String DEMO_VIN = "NL1CFB0P800100522";

    public String getVin() {
        return prefs.getString(KEY_VIN, DEMO_VIN);
    }

    public boolean isDemoVin() {
        String v = getVin();
        return v == null || v.trim().isEmpty() || v.equalsIgnoreCase(DEMO_VIN) || v.equalsIgnoreCase("T10F-AWD-LONG");
    }

    public void setVin(String vin) {
        prefs.edit().putString(KEY_VIN, vin != null ? vin.trim().toUpperCase() : "").apply();
    }

    public String getOwnerId() {
        String ownerId = prefs.getString(KEY_OWNER_ID, "");
        return ownerId != null ? ownerId.trim() : "";
    }

    public void setOwnerId(String ownerId) {
        prefs.edit().putString(KEY_OWNER_ID, ownerId != null ? ownerId.trim() : "").apply();
    }

    public String getPlate() {
        return prefs.getString(KEY_PLATE, "06GDU258");
    }

    public void setPlate(String plate) {
        prefs.edit().putString(KEY_PLATE, plate).apply();
    }

    public String getVehicleName() {
        String name = prefs.getString(KEY_VEHICLE_CUSTOM_NAME, null);
        return (name != null && !name.trim().isEmpty()) ? name.trim() : DEFAULT_VEHICLE_NAME;
    }

    public void setVehicleName(String name) {
        prefs.edit().putString(KEY_VEHICLE_CUSTOM_NAME, name != null ? name.trim() : "").apply();
    }

    public boolean isDemoMode() {
        return prefs.getBoolean(KEY_DEMO_MODE, true);
    }

    public void setDemoMode(boolean demo) {
        prefs.edit().putBoolean(KEY_DEMO_MODE, demo).apply();
    }

    public String getApiEndpoint() {
        String ep = prefs.getString(KEY_API_ENDPOINT, DEFAULT_API_ENDPOINT);
        if (ep == null || ep.trim().isEmpty() || ep.equals("https://tva-api.togg.cloud/info/") || ep.endsWith("/info/")) {
            ep = DEFAULT_API_ENDPOINT;
            setApiEndpoint(ep);
        }
        return ep;
    }

    public void setApiEndpoint(String endpoint) {
        prefs.edit().putString(KEY_API_ENDPOINT, endpoint).apply();
    }

    public long getLastSync() {
        return prefs.getLong(KEY_LAST_SYNC, 0L);
    }

    public void setLastSync(long millis) {
        prefs.edit().putLong(KEY_LAST_SYNC, millis).apply();
    }

    public long getLastRefresh() { return prefs.getLong(KEY_LAST_REFRESH, 0L); }

    public void setLastRefresh(long millis) { prefs.edit().putLong(KEY_LAST_REFRESH, millis).apply(); }

    public void saveActiveTelemetry(VehicleTelemetry t) {
        if (t == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("vin", t.vin); json.put("plate", t.plate);
            json.put("soc", t.batterySocPercent); json.put("range", t.estimatedRangeKm);
            json.put("charging", t.isCharging); json.put("chargePower", t.chargePowerKw);
            json.put("chargeMode", t.chargeMode); json.put("chargeMinutes", t.chargeRemainingMinutes);
            json.put("chargeLimit", t.targetChargeLimitPercent); json.put("capacity", t.batteryTotalCapacityKwh);
            json.put("lastEnergy", t.lastChargeEnergyKwh); json.put("12v", t.lowVoltage12vPercent);
            json.put("12vVolts", t.lowVoltage12vVolts);
            json.put("tfl", t.tirePressureFL); json.put("ttfl", t.tireTempFL);
            json.put("tfr", t.tirePressureFR); json.put("ttfr", t.tireTempFR);
            json.put("trl", t.tirePressureRL); json.put("ttrl", t.tireTempRL);
            json.put("trr", t.tirePressureRR); json.put("ttrr", t.tireTempRR); json.put("tpms", t.tpmsSensorOk);
            json.put("cabin", t.cabinTempCelsius); json.put("targetHvac", t.targetHvacCelsius);
            json.put("outside", t.outdoorTempCelsius); json.put("climate", t.isClimateActive);
            json.put("defrost", t.isDefrostActive); json.put("seatDriver", t.seatHeaterDriver);
            json.put("seatPassenger", t.seatHeaterPassenger); json.put("steering", t.steeringHeaterActive);
            json.put("locked", t.isLocked); json.put("doorFL", t.doorOpenFL); json.put("doorFR", t.doorOpenFR);
            json.put("doorRL", t.doorOpenRL); json.put("doorRR", t.doorOpenRR); json.put("trunk", t.trunkOpen);
            json.put("frunk", t.frunkOpen); json.put("windows", t.windowsOpen); json.put("chargePort", t.chargePortOpen);
            json.put("sam", t.samConnected); json.put("headlights", t.headlightsOn); json.put("ready", t.isReadyToDrive);
            json.put("odometer", t.odometerKm); json.put("driveMode", t.driveMode);
            json.put("tripDistance", t.lastTripDistanceKm); json.put("tripDuration", t.lastTripDurationMin);
            json.put("tripConsumption", t.lastTripAvgConsumption); json.put("tripSpeed", t.lastTripAvgSpeed);
            json.put("lat", t.latitude); json.put("lon", t.longitude); json.put("address", t.parkAddress);
            json.put("parkDuration", t.parkDurationMinutes); json.put("timestamp", t.timestampMillis);
            putTrip(json, "currentTrip", t.currentTrip); putTrip(json, "lastChargeTrip", t.lastChargeTrip);
            putTrip(json, "myTrip", t.myTrip);
            prefs.edit().putString(KEY_ACTIVE_TELEMETRY, json.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static void putTrip(JSONObject json, String key, VehicleTelemetry.TripData trip) throws Exception {
        JSONObject value = new JSONObject();
        value.put("distance", trip.distanceKm); value.put("duration", trip.durationMinutes);
        value.put("consumption", trip.avgConsumption); value.put("speed", trip.avgSpeed);
        json.put(key, value);
    }

    public VehicleTelemetry getActiveTelemetry() {
        String value = prefs.getString(KEY_ACTIVE_TELEMETRY, null);
        if (value == null) return null;
        try {
            JSONObject j = new JSONObject(value);
            return new VehicleTelemetry(j.optString("vin"), j.optString("plate"), j.optInt("soc"), j.optInt("range"),
                    j.optBoolean("charging"), j.optDouble("chargePower"), j.optString("chargeMode"), j.optInt("chargeMinutes"),
                    j.optInt("chargeLimit", 80), j.optDouble("capacity", 88.5), j.optDouble("lastEnergy"),
                    j.optInt("12v"), j.optDouble("12vVolts", 13.2), j.optDouble("tfl"), j.optInt("ttfl"),
                    j.optDouble("tfr"), j.optInt("ttfr"), j.optDouble("trl"), j.optInt("ttrl"), j.optDouble("trr"), j.optInt("ttrr"),
                    j.optBoolean("tpms"), j.optDouble("cabin"), j.optDouble("targetHvac"), j.optDouble("outside"),
                    j.optBoolean("climate"), j.optBoolean("defrost"), j.optInt("seatDriver"), j.optInt("seatPassenger"),
                    j.optBoolean("steering"), j.optBoolean("locked"), j.optBoolean("doorFL"), j.optBoolean("doorFR"),
                    j.optBoolean("doorRL"), j.optBoolean("doorRR"), j.optBoolean("trunk"), j.optBoolean("frunk"),
                    j.optBoolean("windows"), j.optBoolean("chargePort"), j.optBoolean("sam"), j.optLong("odometer"),
                    j.optString("driveMode"), j.optDouble("tripDistance"), j.optInt("tripDuration"),
                    j.optDouble("tripConsumption"), j.optInt("tripSpeed"), j.optDouble("lat"), j.optDouble("lon"),
                    j.optString("address"), j.optInt("parkDuration"), j.optLong("timestamp"),
                    getTrip(j, "currentTrip"), getTrip(j, "lastChargeTrip"), getTrip(j, "myTrip"),
                    j.optBoolean("headlights"), j.optBoolean("ready"));
        } catch (Exception ignored) { return null; }
    }

    public VehicleTelemetry getActiveTelemetry(Context context) {
        return context == null ? getActiveTelemetry() : new SecureTokenStorage(context).getActiveTelemetry();
    }

    private static VehicleTelemetry.TripData getTrip(JSONObject json, String key) {
        JSONObject trip = json.optJSONObject(key);
        return trip == null ? null : new VehicleTelemetry.TripData(trip.optDouble("distance"),
                trip.optInt("duration"), trip.optDouble("consumption"), trip.optInt("speed"));
    }

    public boolean hasValidToken() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public void clearTokens() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply();
    }

    private static final String KEY_HOME_CARDS_ORDER = "home_cards_order";
    private static final String KEY_HOME_CARDS_VISIBLE = "home_cards_visible";
    public static final String DEFAULT_HOME_CARDS = "battery,drive,tires,location,12v";

    public String getHomeCardsOrder() {
        return prefs.getString(KEY_HOME_CARDS_ORDER, DEFAULT_HOME_CARDS);
    }

    public void setHomeCardsOrder(String order) {
        prefs.edit().putString(KEY_HOME_CARDS_ORDER, order).apply();
    }

    public String getHomeCardsVisible() {
        return prefs.getString(KEY_HOME_CARDS_VISIBLE, DEFAULT_HOME_CARDS);
    }

    public void setHomeCardsVisible(String visible) {
        prefs.edit().putString(KEY_HOME_CARDS_VISIBLE, visible).apply();
    }

    private static final String KEY_LAST_KNOWN_LAT = "last_known_lat";
    private static final String KEY_LAST_KNOWN_LON = "last_known_lon";
    private static final String KEY_LAST_KNOWN_ADDR = "last_known_addr";
    private static final String KEY_QUICK_ACTIONS_ORDER = "quick_actions_order";
    private static final String KEY_HEADLIGHTS_ON = "headlights_on";

    public static final String DEFAULT_QUICK_ACTIONS = "lock,trunk";

    private static final String KEY_APP_THEME = "app_theme";

    public String getAppTheme() { return prefs.getString(KEY_APP_THEME, "system"); }

    public void setAppTheme(String theme) {
        if (!"dark".equals(theme) && !"light".equals(theme)) theme = "system";
        prefs.edit().putString(KEY_APP_THEME, theme).apply();
    }

    public void saveLastLocation(double lat, double lon, String addr) {
        if (lat == 0.0 && lon == 0.0) return;
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(KEY_LAST_KNOWN_LAT, String.valueOf(lat));
        ed.putString(KEY_LAST_KNOWN_LON, String.valueOf(lon));
        if (addr != null && !addr.trim().isEmpty() && !addr.contains("belirleniyor")) {
            ed.putString(KEY_LAST_KNOWN_ADDR, addr.trim());
        }
        ed.apply();
    }

    public boolean hasSavedLocation() {
        return prefs.contains(KEY_LAST_KNOWN_LAT) && prefs.contains(KEY_LAST_KNOWN_LON);
    }

    public double getLastLat() {
        String s = prefs.getString(KEY_LAST_KNOWN_LAT, null);
        if (s == null) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getLastLon() {
        String s = prefs.getString(KEY_LAST_KNOWN_LON, null);
        if (s == null) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public String getLastAddress() {
        return prefs.getString(KEY_LAST_KNOWN_ADDR, "");
    }

    public String getQuickActionsOrder() {
        return prefs.getString(KEY_QUICK_ACTIONS_ORDER, DEFAULT_QUICK_ACTIONS);
    }

    public void setQuickActionsOrder(String order) {
        prefs.edit().putString(KEY_QUICK_ACTIONS_ORDER, order != null ? order : DEFAULT_QUICK_ACTIONS).apply();
    }

    public boolean isHeadlightsOn() {
        return prefs.getBoolean(KEY_HEADLIGHTS_ON, false);
    }

    public void setHeadlightsOn(boolean on) {
        prefs.edit().putBoolean(KEY_HEADLIGHTS_ON, on).apply();
    }
}
