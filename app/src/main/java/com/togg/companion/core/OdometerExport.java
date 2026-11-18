package com.togg.companion.core;

import java.time.Instant;
import org.json.JSONObject;

/** Agent-neutral odometer snapshot. No vehicle identity, trip or credential fields. */
public final class OdometerExport {
    public static final long MIN_INTERVAL_MINUTES = 15;
    public static final long MAX_INTERVAL_MINUTES = 525600; // one year
    private OdometerExport() { }

    public static String json(long odometerKm, long vehicleTime, long retrievedTime) throws Exception {
        if (odometerKm <= 0 || vehicleTime <= 0 || retrievedTime <= 0)
            throw new IllegalArgumentException("Kilometre veya araç veri zamanı bilinmiyor.");
        return new JSONObject().put("odometer_km", odometerKm)
                .put("vehicle_data_at", Instant.ofEpochMilli(vehicleTime).toString())
                .put("retrieved_at", Instant.ofEpochMilli(retrievedTime).toString()).toString(2);
    }

    /** First anchor-aligned slot strictly after now; missed slots are not replayed. */
    public static long nextTime(long first, long intervalMinutes, long now) {
        if (first <= 0 || intervalMinutes < MIN_INTERVAL_MINUTES || intervalMinutes > MAX_INTERVAL_MINUTES)
            throw new IllegalArgumentException("Geçersiz gönderim zamanı/aralığı.");
        long step = Math.multiplyExact(intervalMinutes, 60000L);
        if (first > now) return first;
        return Math.addExact(first, Math.multiplyExact((now - first) / step + 1, step));
    }
}
