package com.togg.companion.wear;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Background listener service running on the Samsung Galaxy Watch.
 * Receives data updates from the paired phone over Bluetooth (Wearable Data Layer).
 */
public class WatchWearableListenerService extends WearableListenerService {
    private static final String TAG = "WatchWearListener";
    private static final String PATH_TELEMETRY = "/togg_telemetry";

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                Uri uri = item.getUri();
                if (uri.getPath() != null && uri.getPath().equals(PATH_TELEMETRY)) {
                    try {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        int soc = dataMap.getInt("soc", 78);
                        int rangeKm = dataMap.getInt("range_km", 385);
                        boolean isCharging = dataMap.getBoolean("is_charging", false);
                        double chargePower = dataMap.getDouble("charge_power_kw", 0.0);
                        int batt12v = dataMap.getInt("batt12v_soc", 88);
                        boolean batt12vAlert = dataMap.getBoolean("batt12v_alert", false);
                        boolean tiresNormal = dataMap.getBoolean("tires_normal", true);
                        String tiresSummary = dataMap.getString("tires_summary", "Normal (36 PSI)");
                        double lat = dataMap.getDouble("latitude", 40.9901);
                        double lon = dataMap.getDouble("longitude", 29.0292);
                        String address = dataMap.getString("park_address", "Kadıköy, İstanbul");
                        long ts = dataMap.getLong("timestamp", System.currentTimeMillis());

                        WatchTelemetry telemetry = new WatchTelemetry(
                                soc, rangeKm, isCharging, chargePower,
                                batt12v, batt12vAlert, tiresNormal, tiresSummary,
                                lat, lon, address, ts
                        );

                        Log.d(TAG, "Received telemetry from phone: SoC=" + soc + "%, 12V=" + batt12v + "%, Tires=" + tiresSummary);
                        WatchDataStore.saveTelemetry(this, telemetry);

                        // Trigger Wear OS Tile refresh
                        ToggTileService.requestTileUpdate(this);

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing telemetry DataMap", e);
                    }
                }
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if (PATH_TELEMETRY.equals(messageEvent.getPath())) {
            try {
                String jsonStr = new String(messageEvent.getData(), StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(jsonStr);
                WatchTelemetry current = WatchDataStore.getTelemetry(this);
                WatchTelemetry updated = new WatchTelemetry(
                        obj.optInt("soc", current.soc),
                        obj.optInt("range", current.rangeKm),
                        obj.optBoolean("chg", current.isCharging),
                        current.chargePowerKw,
                        obj.optInt("b12", current.batt12vSoc),
                        current.batt12vAlert,
                        obj.optBoolean("tire", current.tiresNormal),
                        current.tiresSummary,
                        obj.optDouble("lat", current.latitude),
                        obj.optDouble("lon", current.longitude),
                        current.parkAddress,
                        obj.optLong("ts", System.currentTimeMillis())
                );
                WatchDataStore.saveTelemetry(this, updated);
                ToggTileService.requestTileUpdate(this);
            } catch (Exception e) {
                Log.e(TAG, "Error handling message telemetry", e);
            }
        }
    }
}
