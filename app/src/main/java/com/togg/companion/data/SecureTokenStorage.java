package com.togg.companion.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Encapsulates persistent app settings and authorization tokens.
 */
public final class SecureTokenStorage {
    private static final String PREFS_NAME = "togg_companion_secure_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_VIN = "vehicle_vin";
    private static final String KEY_PLATE = "vehicle_plate";
    private static final String KEY_DEMO_MODE = "is_demo_mode";
    private static final String KEY_API_ENDPOINT = "api_endpoint";
    private static final String KEY_LAST_SYNC = "last_sync_millis";

    public static final String DEFAULT_API_ENDPOINT = "https://tva-api.togg.cloud/info/api/v2/VehicleInfo";

    private final SharedPreferences prefs;

    public SecureTokenStorage(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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

    public String getPlate() {
        return prefs.getString(KEY_PLATE, "06GDU258");
    }

    public void setPlate(String plate) {
        prefs.edit().putString(KEY_PLATE, plate).apply();
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

    public boolean hasValidToken() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public void clearTokens() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).apply();
    }

    private static final String KEY_HOME_CARDS_ORDER = "home_cards_order";
    private static final String KEY_HOME_CARDS_VISIBLE = "home_cards_visible";
    public static final String DEFAULT_HOME_CARDS = "battery,controls,drive,tires,location,climate,12v";

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
}
