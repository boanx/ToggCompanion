package com.togg.companion.core;

import java.util.Iterator;
import java.util.Locale;
import org.json.JSONObject;

/** Charging type is not a power measurement. -1 denotes unavailable power; zero is valid. */
public final class ChargingInfo {
    private ChargingInfo() { }
    public static boolean knownPower(double power) { return Double.isFinite(power) && power >= 0; }
    public static String powerText(double power) {
        return knownPower(power) ? String.format(Locale.getDefault(), "%.1f kW", power) : "Bilgi yok";
    }
    public static String statusText(boolean charging, double power) {
        if (!charging) return "Şarj olmuyor";
        return "Şarj oluyor" + (knownPower(power) ? "- " + new java.text.DecimalFormat("0.##").format(power) + "kW" : "");
    }
    public static String mode(String raw) {
        String value = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if (value.contains("NOT_") || value.contains("NO_") || value.contains("INACTIVE")
                || value.contains("DISCONNECT") || value.contains("STOPPED") || value.contains("COMPLETE")) return "";
        if (value.matches(".*(?:^|_)(DC|TRUGO|FAST|HIZLI)(?:_|$).*")) return "DC Şarj";
        if (value.matches(".*(?:^|_)(AC|SLOW|YAVAS)(?:_|$).*")) return "AC Şarj";
        if (value.matches(".*(?:^|_)(CHARGING|CHARGE)(?:_|$).*")) return "Şarj Oluyor";
        return "";
    }
    /** Unlike Boolean.parseBoolean, unknown status text is not explicit false. */
    public static Boolean directState(JSONObject telemetry) { return directState(telemetry, 0); }
    private static Boolean directState(JSONObject object, int depth) {
        if (object == null || depth > 5) return null;
        for (String key : new String[]{"isCharging", "chargingStatus", "charging"}) {
            Boolean state = stateValue(object.opt(key));
            if (state != null) return state;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (excluded(key)) continue;
            Boolean state = directState(object.optJSONObject(key), depth + 1);
            if (state != null) return state;
        }
        return null;
    }
    private static Boolean stateValue(Object raw) {
        if (raw instanceof JSONObject) return stateValue(((JSONObject) raw).opt("value"));
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) {
            double number = ((Number) raw).doubleValue();
            return number == 1 ? Boolean.TRUE : number == 0 ? Boolean.FALSE : null;
        }
        if (!(raw instanceof String)) return null;
        String value = ((String) raw).trim().replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT).replaceAll("[ .-]+", "_");
        switch (value) {
            case "TRUE": case "1": case "CHARGING": case "CHARGE": case "CHRGMODE_CHARGE": return true;
            case "FALSE": case "0": case "NOT_CHARGING": case "NO_CHARGING": case "DISCONNECTED":
            case "INACTIVE": case "STOPPED": case "COMPLETE": case "COMPLETED": case "STANDBY":
            case "CHRGMODE_STANDBY": return false;
            default: return null;
        }
    }
    private static boolean excluded(String key) {
        return key.equals("lastTripInfo") || key.equals("currentTrip") || key.equals("myTrip")
                || key.equals("lastChargeTrip") || key.equals("chargingStation");
    }
    /** Only explicit charging power keys, never arbitrary motor/12V 'power' values or rated capacity. */
    private static final class Reading {
        double power = -1;
        int present, invalid;
        String key = "NONE";
    }
    public static double readPower(JSONObject telemetry) {
        Reading reading = new Reading(); inspect(telemetry, 0, reading); return reading.power;
    }
    private static void inspect(JSONObject object, int depth, Reading reading) {
        if (object == null || depth > 5) return;
        for (String key : new String[]{"chargePowerKw", "chargingPower"}) {
            if (!object.has(key)) continue;
            reading.present++;
            Object value = object.opt(key);
            if (value instanceof JSONObject) value = ((JSONObject) value).opt("value");
            try {
                double power = value instanceof Number ? ((Number) value).doubleValue()
                        : value instanceof String ? Double.parseDouble(((String) value).trim()) : -1;
                if (knownPower(power)) {
                    if (!knownPower(reading.power)) { reading.power = power; reading.key = key; }
                } else reading.invalid++;
            } catch (NumberFormatException ignored) { reading.invalid++; }
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            // Historical trips and station specifications cannot describe instantaneous charging power.
            if (excluded(key)) continue;
            inspect(object.optJSONObject(key), depth + 1, reading);
        }
    }
    /** Only allowlisted field identifiers, numeric measurement and timestamps; never raw response data. */
    public static String diagnostic(String body, boolean charging, long vehicleTime) {
        try {
            JSONObject root = new JSONObject(body), data = root.optJSONObject("data");
            if (data == null) data = root;
            JSONObject telemetry = data.optJSONObject("telemetry");
            if (telemetry == null) telemetry = data;
            Reading reading = new Reading(); inspect(telemetry, 0, reading);
            Boolean direct = directState(telemetry);
            return "CLOUD_RESPONSE charging=" + charging + " directState=" + (direct == null ? "UNKNOWN" : direct)
                    + " supportedFields=" + reading.present
                    + " invalidFields=" + reading.invalid + " selectedField=" + reading.key
                    + " reportedKw=" + (knownPower(reading.power) ? Double.toString(reading.power) : "UNAVAILABLE")
                    + " usedForDisplay=" + (charging && knownPower(reading.power)) + " vehicleDataTime=" + vehicleTime;
        } catch (Exception error) { return "DIAGNOSTIC_UNAVAILABLE"; }
    }
}
