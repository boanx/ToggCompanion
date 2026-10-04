package com.togg.companion.data.remote;

import com.togg.companion.core.VehicleTelemetry;
import org.json.JSONObject;
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
    public static final String API_KEY = "32b2162f-9599-4647-8139-66e9f9528370";
    private AbrpClient() {}

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
