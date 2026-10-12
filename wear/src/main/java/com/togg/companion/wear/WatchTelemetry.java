package com.togg.companion.wear;

import android.location.Location;
import java.io.Serializable;
import java.util.Locale;

/**
 * Minimal data model for Samsung Galaxy Watch (Wear OS).
 * Encapsulates the 4 core vehicle metrics:
 * 1. High-Voltage Battery & Range
 * 2. 12V Auxiliary Battery Level
 * 3. Tire Pressure Status (Normal / Low)
 * 4. Vehicle Location & Distance
 */
public final class WatchTelemetry implements Serializable {
    public final int soc;                  // 0 - 100%
    public final int rangeKm;             // e.g. 385 km
    public final boolean isCharging;
    public final double chargePowerKw;    // e.g. 11.0 kW
    public final int batt12vSoc;          // 0 - 100%
    public final boolean batt12vAlert;    // true if < 60%
    public final boolean tiresNormal;     // true if all 4 tires in safe PSI
    public final String tiresSummary;     // "Normal (36 PSI)" or "Ön Sol 29 PSI"
    public final double latitude;
    public final double longitude;
    public final String parkAddress;      // e.g. "Kadıköy, İstanbul"
    public final long timestamp;          // epoch millis

    public WatchTelemetry(
            int soc, int rangeKm, boolean isCharging, double chargePowerKw,
            int batt12vSoc, boolean batt12vAlert, boolean tiresNormal, String tiresSummary,
            double latitude, double longitude, String parkAddress, long timestamp) {
        this.soc = Math.max(0, Math.min(100, soc));
        this.rangeKm = Math.max(0, rangeKm);
        this.isCharging = isCharging;
        this.chargePowerKw = Math.max(0.0, chargePowerKw);
        this.batt12vSoc = Math.max(0, Math.min(100, batt12vSoc));
        this.batt12vAlert = batt12vAlert || (this.batt12vSoc < 60);
        this.tiresNormal = tiresNormal;
        this.tiresSummary = tiresSummary != null ? tiresSummary : (tiresNormal ? "Normal (36 PSI)" : "Düşük Basınç");
        this.latitude = latitude;
        this.longitude = longitude;
        this.parkAddress = parkAddress != null ? parkAddress : "Konum alınıyor...";
        this.timestamp = Math.max(0, timestamp);
    }

    public static WatchTelemetry createDefault() {
        return new WatchTelemetry(
                0, 0, false, 0.0,
                0, false, true, "Henüz senkronize edilmedi",
                0.0, 0.0, "Araç verisi telefondan bekleniyor",
                0
        );
    }

    public String getRelativeTimeString() {
        if (timestamp <= 0) return "Henüz senkronize edilmedi";
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long minutes = diff / (60 * 1000);
        if (minutes < 1) return "Az önce";
        if (minutes < 60) return minutes + " dk önce";
        long hours = minutes / 60;
        if (hours < 24) return hours + " sa önce";
        return (hours / 24) + " gün önce";
    }

    /**
     * Calculates distance and compass direction arrow from user's current watch location.
     */
    public String calculateDistanceString(Location watchLocation) {
        if (watchLocation == null || (latitude == 0.0 && longitude == 0.0)) {
            return "Konum hazır";
        }
        float[] results = new float[2];
        Location.distanceBetween(watchLocation.getLatitude(), watchLocation.getLongitude(), latitude, longitude, results);
        float distanceMeters = results[0];
        float bearing = results.length > 1 ? results[1] : 0.0f;

        String arrow = getBearingArrow(bearing);
        if (distanceMeters < 1000) {
            return String.format(Locale.getDefault(), "%.0f m %s", distanceMeters, arrow);
        } else {
            return String.format(Locale.getDefault(), "%.1f km %s", distanceMeters / 1000.0, arrow);
        }
    }

    private static String getBearingArrow(float bearing) {
        // Normalize bearing to 0 - 360
        float b = (bearing % 360 + 360) % 360;
        if (b >= 337.5 || b < 22.5) return "⬆️";
        if (b >= 22.5 && b < 67.5) return "↗️";
        if (b >= 67.5 && b < 112.5) return "➡️";
        if (b >= 112.5 && b < 157.5) return "↘️";
        if (b >= 157.5 && b < 202.5) return "⬇️";
        if (b >= 202.5 && b < 247.5) return "↙️";
        if (b >= 247.5 && b < 292.5) return "⬅️";
        return "↖️";
    }
}
