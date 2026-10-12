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
    private static final String PATH_DIGITAL_KEY = "/togg_digital_key";
    private static final String PATH_KEY_ACTION_RESULT = "/key_action_result";

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
                        if (!dataMap.containsKey("soc") || !dataMap.containsKey("range_km")) {
                            Log.w(TAG, "Ignored incomplete telemetry DataMap");
                            continue;
                        }
                        int soc = dataMap.getInt("soc", 0);
                        int rangeKm = dataMap.getInt("range_km", 0);
                        boolean isCharging = dataMap.getBoolean("is_charging", false);
                        double chargePower = dataMap.getDouble("charge_power_kw", 0.0);
                        int batt12v = dataMap.getInt("batt12v_soc", 0);
                        boolean batt12vAlert = dataMap.getBoolean("batt12v_alert", false);
                        boolean tiresNormal = dataMap.getBoolean("tires_normal", false);
                        String tiresSummary = dataMap.getString("tires_summary", "Lastik verisi alınmadı");
                        double lat = dataMap.getDouble("latitude", 0.0);
                        double lon = dataMap.getDouble("longitude", 0.0);
                        String address = dataMap.getString("park_address", "Araç konumu alınmadı");
                        long ts = dataMap.getLong("timestamp", 0);

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
                } else if (uri.getPath() != null && uri.getPath().equals(PATH_DIGITAL_KEY)) {
                    try {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        boolean isActive = dataMap.getBoolean("dk_is_active", false);
                        if (isActive) {
                            WatchDataStore.WatchDigitalKey key = new WatchDataStore.WatchDigitalKey(
                                    true,
                                    dataMap.getString("dk_id", ""),
                                    dataMap.getString("dk_se_id", ""),
                                    dataMap.getString("dk_vin", ""),
                                    dataMap.getString("dk_plate", ""),
                                    dataMap.getString("dk_holder", "Paylaşılan Kullanıcı"),
                                    dataMap.getLong("dk_valid_until", 0),
                                    dataMap.getString("dk_token", ""),
                                    dataMap.getBoolean("dk_can_lock", true),
                                    dataMap.getBoolean("dk_can_unlock", true)
                            );
                            WatchDataStore.saveDigitalKey(this, key);
                            Log.d(TAG, "Watch received and saved Digital Key: " + key.seId);
                        } else {
                            WatchDataStore.removeDigitalKey(this);
                            Log.d(TAG, "Watch received digital key removal");
                        }
                        ToggTileService.requestTileUpdate(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing digital key DataMap", e);
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
        } else if (PATH_DIGITAL_KEY.equals(messageEvent.getPath())) {
            try {
                String jsonStr = new String(messageEvent.getData(), StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(jsonStr);
                boolean isActive = obj.optBoolean("isActive", true);
                if (isActive) {
                    WatchDataStore.WatchDigitalKey key = new WatchDataStore.WatchDigitalKey(
                            true,
                            obj.optString("keyId"),
                            obj.optString("seId"),
                            obj.optString("vehicleVin", ""),
                            obj.optString("vehiclePlate", ""),
                            obj.optString("recipientName", "Paylaşılan Kullanıcı"),
                            obj.optLong("validUntilMillis", 0),
                            obj.optString("keyToken"),
                            obj.optBoolean("canLock", true),
                            obj.optBoolean("canUnlock", true)
                    );
                    WatchDataStore.saveDigitalKey(this, key);
                    Log.d(TAG, "Watch received message Digital Key: " + key.seId);
                } else {
                    WatchDataStore.removeDigitalKey(this);
                }
                ToggTileService.requestTileUpdate(this);
            } catch (Exception e) {
                Log.e(TAG, "Error handling message digital key", e);
            }
        } else if (PATH_KEY_ACTION_RESULT.equals(messageEvent.getPath())) {
            try {
                JSONObject result = new JSONObject(new String(messageEvent.getData(), StandardCharsets.UTF_8));
                String message = result.optString("message", "Komut sonucu alınamadı.");
                boolean success = result.optBoolean("success", false);
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
                if (success) ToggTileService.requestTileUpdate(this);
            } catch (Exception e) {
                Log.e(TAG, "Error handling watch action result", e);
            }
        }
    }
}
