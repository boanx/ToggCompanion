package com.togg.companion.wearable;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.togg.companion.core.VehicleTelemetry;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Handles seamless data synchronization between the phone app and Samsung Galaxy Watch (Wear OS).
 */
public final class WearSyncManager {
    private static final String TAG = "WearSyncManager";

    public static final String PATH_TELEMETRY = "/togg_telemetry";
    public static final String PATH_REQUEST_SYNC = "/request_telemetry";
    public static final String PATH_DIGITAL_KEY = "/togg_digital_key";
    public static final String PATH_REQUEST_KEY = "/request_digital_key";
    public static final String PATH_KEY_ACTION = "/execute_key_action";

    public static final String KEY_SOC = "soc";
    public static final String KEY_RANGE_KM = "range_km";
    public static final String KEY_IS_CHARGING = "is_charging";
    public static final String KEY_CHARGE_POWER_KW = "charge_power_kw";
    public static final String KEY_BATT_12V_SOC = "batt12v_soc";
    public static final String KEY_BATT_12V_ALERT = "batt12v_alert";
    public static final String KEY_TIRES_NORMAL = "tires_normal";
    public static final String KEY_TIRES_SUMMARY = "tires_summary";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    public static final String KEY_PARK_ADDRESS = "park_address";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_SYNC_ID = "sync_id";

    // Digital Key fields
    public static final String KEY_DK_ID = "dk_id";
    public static final String KEY_DK_SE_ID = "dk_se_id";
    public static final String KEY_DK_VIN = "dk_vin";
    public static final String KEY_DK_PLATE = "dk_plate";
    public static final String KEY_DK_HOLDER = "dk_holder";
    public static final String KEY_DK_VALID_UNTIL = "dk_valid_until";
    public static final String KEY_DK_TOKEN = "dk_token";
    public static final String KEY_DK_CAN_LOCK = "dk_can_lock";
    public static final String KEY_DK_CAN_UNLOCK = "dk_can_unlock";
    public static final String KEY_DK_CAN_TRUNK = "dk_can_trunk";
    public static final String KEY_DK_CAN_CLIMATE = "dk_can_climate";
    public static final String KEY_DK_IS_ACTIVE = "dk_is_active";
    public static final String KEY_DK_SYNC_TIME = "dk_sync_time";

    private WearSyncManager() {}

    public static void syncDigitalKey(Context context, com.togg.companion.core.DigitalKey digitalKey) {
        if (context == null) return;

        new Thread(() -> {
            try {
                PutDataMapRequest putDataMapReq = PutDataMapRequest.create(PATH_DIGITAL_KEY);
                boolean hasKey = digitalKey != null && digitalKey.isValid();
                putDataMapReq.getDataMap().putBoolean(KEY_DK_IS_ACTIVE, hasKey);

                if (hasKey) {
                    putDataMapReq.getDataMap().putString(KEY_DK_ID, digitalKey.keyId);
                    putDataMapReq.getDataMap().putString(KEY_DK_SE_ID, digitalKey.seId);
                    putDataMapReq.getDataMap().putString(KEY_DK_VIN, digitalKey.vehicleVin);
                    putDataMapReq.getDataMap().putString(KEY_DK_PLATE, digitalKey.vehiclePlate);
                    putDataMapReq.getDataMap().putString(KEY_DK_HOLDER, digitalKey.recipientName);
                    putDataMapReq.getDataMap().putLong(KEY_DK_VALID_UNTIL, digitalKey.validUntilMillis);
                    putDataMapReq.getDataMap().putString(KEY_DK_TOKEN, digitalKey.keyToken);
                    putDataMapReq.getDataMap().putBoolean(KEY_DK_CAN_LOCK, digitalKey.canLock);
                    putDataMapReq.getDataMap().putBoolean(KEY_DK_CAN_UNLOCK, digitalKey.canUnlock);
                    putDataMapReq.getDataMap().putBoolean(KEY_DK_CAN_TRUNK, digitalKey.canTrunk);
                    putDataMapReq.getDataMap().putBoolean(KEY_DK_CAN_CLIMATE, digitalKey.canClimate);
                } else {
                    putDataMapReq.getDataMap().putString(KEY_DK_ID, "");
                    putDataMapReq.getDataMap().putString(KEY_DK_TOKEN, "");
                }
                putDataMapReq.getDataMap().putLong(KEY_DK_SYNC_TIME, System.currentTimeMillis());

                PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                putDataReq.setUrgent();

                DataClient dataClient = Wearable.getDataClient(context);
                dataClient.putDataItem(putDataReq).addOnSuccessListener(item -> {
                    Log.d(TAG, "Digital Key synced to watch: " + (hasKey ? digitalKey.seId : "REMOVED"));
                }).addOnFailureListener(e -> Log.w(TAG, "Failed to sync Digital Key to watch: " + e.getMessage()));

                // Broadcast directly via message to connected nodes
                Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(nodes -> {
                    if (nodes != null) {
                        String payload = hasKey ? digitalKey.toJson().toString() : "{\"isActive\":false}";
                        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                        for (Node node : nodes) {
                            Wearable.getMessageClient(context).sendMessage(node.getId(), PATH_DIGITAL_KEY, bytes);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error syncing digital key to watch", e);
            }
        }).start();
    }

    public static void syncTelemetry(Context context, VehicleTelemetry telemetry) {
        if (context == null || telemetry == null) return;

        new Thread(() -> {
            try {
                // Calculate Tire Status
                boolean isAnyLow = telemetry.isAnyTireLow();
                String tiresSummary;
                if (!isAnyLow) {
                    double avgPsi = (telemetry.getTirePressurePsiFL() + telemetry.getTirePressurePsiFR()
                            + telemetry.getTirePressurePsiRL() + telemetry.getTirePressurePsiRR()) / 4.0;
                    tiresSummary = String.format(Locale.getDefault(), "Normal (%.0f PSI)", avgPsi > 0 ? avgPsi : 36.0);
                } else {
                    // Find lowest tire
                    double minPsi = Math.min(Math.min(telemetry.getTirePressurePsiFL(), telemetry.getTirePressurePsiFR()),
                            Math.min(telemetry.getTirePressurePsiRL(), telemetry.getTirePressurePsiRR()));
                    tiresSummary = String.format(Locale.getDefault(), "Düşük (%.0f PSI)", minPsi);
                }

                // 1. Put into Wearable DataClient (persistent synced state)
                PutDataMapRequest putDataMapReq = PutDataMapRequest.create(PATH_TELEMETRY);
                putDataMapReq.getDataMap().putInt(KEY_SOC, telemetry.batterySocPercent);
                putDataMapReq.getDataMap().putInt(KEY_RANGE_KM, telemetry.estimatedRangeKm);
                putDataMapReq.getDataMap().putBoolean(KEY_IS_CHARGING, telemetry.isCharging);
                putDataMapReq.getDataMap().putBoolean("is_ready_to_drive", telemetry.isReadyToDrive);
                putDataMapReq.getDataMap().putDouble(KEY_CHARGE_POWER_KW, telemetry.chargePowerKw);
                putDataMapReq.getDataMap().putInt(KEY_BATT_12V_SOC, telemetry.lowVoltage12vPercent);
                putDataMapReq.getDataMap().putBoolean(KEY_BATT_12V_ALERT, telemetry.is12vBatteryLow());
                putDataMapReq.getDataMap().putBoolean(KEY_TIRES_NORMAL, !isAnyLow);
                putDataMapReq.getDataMap().putString(KEY_TIRES_SUMMARY, tiresSummary);
                putDataMapReq.getDataMap().putDouble(KEY_LATITUDE, telemetry.latitude);
                putDataMapReq.getDataMap().putDouble(KEY_LONGITUDE, telemetry.longitude);
                putDataMapReq.getDataMap().putString(KEY_PARK_ADDRESS, telemetry.parkAddress);
                putDataMapReq.getDataMap().putLong(KEY_TIMESTAMP, telemetry.timestampMillis);
                putDataMapReq.getDataMap().putLong(KEY_SYNC_ID, System.currentTimeMillis());

                PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                putDataReq.setUrgent();

                DataClient dataClient = Wearable.getDataClient(context);
                Task<DataItem> dataItemTask = dataClient.putDataItem(putDataReq);
                dataItemTask.addOnSuccessListener(dataItem -> Log.d(TAG, "DataMap sent to watch: " + dataItem.getUri()))
                        .addOnFailureListener(e -> Log.w(TAG, "Failed to send DataMap to watch: " + e.getMessage()));

                // 2. Also broadcast directly via MessageClient to connected nodes
                Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(nodes -> {
                    if (nodes != null) {
                        String payload = String.format(Locale.US,
                                "{\"soc\":%d,\"range\":%d,\"chg\":%b,\"ready\":%b,\"b12\":%d,\"tire\":%b,\"lat\":%f,\"lon\":%f,\"ts\":%d}",
                                telemetry.batterySocPercent, telemetry.estimatedRangeKm, telemetry.isCharging, telemetry.isReadyToDrive,
                                telemetry.lowVoltage12vPercent, !isAnyLow, telemetry.latitude, telemetry.longitude,
                                telemetry.timestampMillis);
                        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                        for (Node node : nodes) {
                            Wearable.getMessageClient(context).sendMessage(node.getId(), PATH_TELEMETRY, bytes);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in syncTelemetry", e);
            }
        }).start();
    }
}
