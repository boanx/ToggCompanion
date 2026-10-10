package com.togg.companion.data.remote;

import com.togg.companion.core.VehicleTelemetry;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Client for ABRP's telemetry endpoint. */
public final class AbrpClient {
    public static final String ENDPOINT = "https://api.iternio.com/1/tlm/send";
    public static final String LATEST_PLAN_ENDPOINT = "https://api.iternio.com/1/tlm/get_latest_plan";
    public static final String API_KEY = "32b2162f-9599-4647-8139-66e9f9528370";
    private AbrpClient() {}

    public static final class AbrpPlanStop {
        public final String name;
        public final double lat, lon, arrivalSoc;
        public final long distanceMeters, durationSeconds;
        AbrpPlanStop(String name, double lat, double lon, double arrivalSoc, long distanceMeters, long durationSeconds) {
            this.name = name; this.lat = lat; this.lon = lon; this.arrivalSoc = arrivalSoc;
            this.distanceMeters = distanceMeters; this.durationSeconds = durationSeconds;
        }
        public String key() { return String.format(java.util.Locale.US, "%.5f,%.5f", lat, lon); }
    }

    public static AbrpPlanStop fetchLatestPlan(String token) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new IllegalArgumentException("ABRP token is required");
        String query = "api_key=" + URLEncoder.encode(API_KEY, "UTF-8") + "&token=" + URLEncoder.encode(token.trim(), "UTF-8");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LATEST_PLAN_ENDPOINT + "?" + query).openConnection();
            connection.setRequestMethod("GET"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new java.io.IOException("ABRP latest plan returned HTTP " + code);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) throw new java.io.IOException("ABRP latest plan returned HTTP " + code);
            AbrpPlanStop stop = parsePlanJson(body.toString());
            if (stop == null) throw new java.io.IOException("ABRP response contains no upcoming stop");
            return stop;
        } finally { if (connection != null) connection.disconnect(); }
    }

    public static AbrpPlanStop parsePlanJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject stop = findNamedStop(root, new String[]{"next_stop", "nextStop", "next_waypoint", "nextWaypoint", "next_destination", "nextDestination"});
            if (stop == null) stop = findNamedStop(root, new String[]{"stop", "destination", "waypoint", "target"});
            if (stop == null) stop = findFirstStopArray(root, new String[]{"stops", "waypoints", "destinations", "route"});
            return stop == null ? null : parseStop(stop);
        } catch (JSONException ignored) { return null; }
    }

    private static JSONObject findNamedStop(JSONObject object, String[] keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof JSONObject && parseStop((JSONObject) value) != null) return (JSONObject) value;
            if (value instanceof JSONArray && ((JSONArray) value).length() > 0) {
                JSONObject candidate = ((JSONArray) value).optJSONObject(0);
                if (candidate != null && parseStop(candidate) != null) return candidate;
            }
        }
        java.util.Iterator<String> names = object.keys();
        while (names.hasNext()) {
            Object value = object.opt(names.next());
            if (value instanceof JSONObject) { JSONObject found = findNamedStop((JSONObject) value, keys); if (found != null) return found; }
        }
        return null;
    }

    private static JSONObject findFirstStopArray(JSONObject object, String[] keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject candidate = array.optJSONObject(i);
                    if (candidate != null && parseStop(candidate) != null) return candidate;
                }
            }
        }
        java.util.Iterator<String> names = object.keys();
        while (names.hasNext()) {
            Object value = object.opt(names.next());
            if (value instanceof JSONObject) { JSONObject found = findFirstStopArray((JSONObject) value, keys); if (found != null) return found; }
        }
        return null;
    }

    private static AbrpPlanStop parseStop(JSONObject object) {
        JSONObject coordinates = object.optJSONObject("location");
        if (coordinates == null) coordinates = object.optJSONObject("coordinates");
        if (coordinates == null) coordinates = object;
        double lat = number(coordinates, "lat", "latitude"), lon = number(coordinates, "lon", "lng", "longitude");
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180 || (lat == 0 && lon == 0)) return null;
        String name = firstString(object, "name", "title", "address", "label");
        return new AbrpPlanStop(name == null ? "ABRP hedefi" : name, lat, lon,
                optionalNumber(object, "arrival_soc", "arrivalSoc", "soc_arrival", "soc"),
                (long) optionalNumber(object, "distance_meters", "distanceMeters", "distance_m", "distance"),
                (long) optionalNumber(object, "duration_seconds", "durationSeconds", "duration_s", "duration"));
    }

    private static double number(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof Number) return ((Number) value).doubleValue();
            if (value instanceof String) try { return Double.parseDouble((String) value); } catch (NumberFormatException ignored) { }
        }
        return Double.NaN;
    }

    private static double optionalNumber(JSONObject object, String... keys) {
        double value = number(object, keys);
        return Double.isFinite(value) ? value : 0;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) { String value = object.optString(key, "").trim(); if (!value.isEmpty()) return value; }
        return null;
    }

    public static JSONObject buildTlmJson(VehicleTelemetry t, double capacityKwh) {
        JSONObject json = new JSONObject();
        try {
            json.put("utc", System.currentTimeMillis() / 1000L);
            json.put("soc", t.batterySocPercent);
            json.put("est_battery_range", t.estimatedRangeKm);
            json.put("odometer", t.odometerKm);
            double consumption = t.lastTripAvgConsumption > 0 ? t.lastTripAvgConsumption :
                    (t.currentTrip != null && t.currentTrip.avgConsumption > 0 ? t.currentTrip.avgConsumption : 0);
            json.put("consumption", consumption);
            json.put("capacity", capacityKwh);
            json.put("soe", (t.batterySocPercent / 100.0) * capacityKwh);
            json.put("is_charging", t.isCharging ? 1 : 0);
            json.put("ext_temp", t.outdoorTempCelsius);
            if (t.latitude != 0.0 && t.longitude != 0.0) { json.put("lat", t.latitude); json.put("lon", t.longitude); }
        } catch (Exception ignored) { }
        return json;
    }

    public static boolean sendTelemetry(String token, VehicleTelemetry telemetry, double capacityKwh) {
        if (token == null || token.trim().isEmpty() || telemetry == null) return false;
        HttpURLConnection connection = null;
        try {
            String query = "api_key=" + URLEncoder.encode(API_KEY, "UTF-8") + "&token=" + URLEncoder.encode(token.trim(), "UTF-8") + "&tlm=" + URLEncoder.encode(buildTlmJson(telemetry, capacityKwh).toString(), "UTF-8");
            connection = (HttpURLConnection) new URL(ENDPOINT + "?" + query).openConnection();
            connection.setRequestMethod("GET"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) body.append(line); }
            return code >= 200 && code < 300;
        } catch (Exception error) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }
}
