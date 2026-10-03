package com.togg.companion.wearable;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.togg.companion.core.DigitalKey;
import com.togg.companion.core.DigitalKeyManager;
import com.togg.companion.core.VehicleBleManager;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.ui.MainActivity;

/**
 * Listens for requests from Samsung Galaxy Watch (Wear OS) and replies with fresh vehicle telemetry.
 */
public class PhoneWearListenerService extends WearableListenerService {
    private static final String TAG = "PhoneWearListener";

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if (messageEvent.getPath().equals(WearSyncManager.PATH_REQUEST_SYNC)) {
            Log.d(TAG, "Watch requested telemetry sync");
            // Check active telemetry from MainActivity or Database
            VehicleTelemetry telemetry = null;
            if (MainActivity.getInstance() != null) {
                telemetry = MainActivity.getInstance().getCurrentTelemetry();
            }
            if (telemetry == null) {
                TelemetryDatabaseHelper dbHelper = new TelemetryDatabaseHelper(this);
                telemetry = dbHelper.getLatest();
            }

            if (telemetry != null) {
                Log.d(TAG, "Responding to watch with telemetry from " + telemetry.timestampMillis);
                WearSyncManager.syncTelemetry(this, telemetry);
            } else {
                Log.w(TAG, "No telemetry available to send to watch");
            }
        } else if (messageEvent.getPath().equals(WearSyncManager.PATH_REQUEST_KEY)) {
            Log.d(TAG, "Watch requested digital key sync");
            com.togg.companion.core.DigitalKeyManager keyMgr = new com.togg.companion.core.DigitalKeyManager(this);
            com.togg.companion.core.DigitalKey key = keyMgr.getActiveKey();
            WearSyncManager.syncDigitalKey(this, key);
        } else if (messageEvent.getPath().equals(WearSyncManager.PATH_KEY_ACTION)) {
            String data = new String(messageEvent.getData(), java.nio.charset.StandardCharsets.UTF_8);
            if (!isValidKeyAction(data)) {
                Log.w(TAG, "Rejected invalid watch key action");
                return;
            }
            String sourceNodeId = messageEvent.getSourceNodeId();
            Wearable.getNodeClient(this).getConnectedNodes()
                    .addOnSuccessListener(nodes -> {
                        boolean connected = false;
                        for (Node node : nodes) {
                            if (node.getId().equals(sourceNodeId)) { connected = true; break; }
                        }
                        if (!connected) {
                            Log.w(TAG, "Rejected watch key action from unconnected node");
                            return;
                        }
                        dispatchVerifiedKeyAction(data);
                    })
                    .addOnFailureListener(error -> Log.e(TAG, "Unable to verify watch sender node", error));
        }
    }

    private static boolean isValidKeyAction(String action) {
        switch (action) {
            case "lock": case "unlock": case "trunk": case "emotor_start":
            case "emotor_stop": case "refresh": return true;
            default: return false;
        }
    }

    private void dispatchVerifiedKeyAction(String action) {
        if ("refresh".equals(action)) {
            WearSyncManager.syncDigitalKey(this, new DigitalKeyManager(this).getActiveKey());
            VehicleTelemetry telemetry = new TelemetryDatabaseHelper(this).getLatest();
            if (telemetry != null) WearSyncManager.syncTelemetry(this, telemetry);
            return;
        }
        MainActivity activity = MainActivity.getInstance();
        if (activity != null && !action.startsWith("emotor_")) {
            activity.runOnUiThread(() -> activity.executeDigitalKeyAction(action));
            return;
        }
        DigitalKey key = new DigitalKeyManager(this).getActiveKey();
        if (key == null || !key.isValid()) {
            Log.w(TAG, "Rejected watch key action without active digital key");
            return;
        }
        VehicleBleManager.VehicleCommand command;
        switch (action) {
            case "lock": if (!key.canLock) return; command = VehicleBleManager.VehicleCommand.LOCK; break;
            case "unlock": if (!key.canUnlock) return; command = VehicleBleManager.VehicleCommand.UNLOCK; break;
            case "trunk": if (!key.canTrunk) return; command = VehicleBleManager.VehicleCommand.TRUNK; break;
            case "emotor_start": if (!key.canStartDrive) return; command = VehicleBleManager.VehicleCommand.EMOTOR_START; break;
            case "emotor_stop": if (!key.canStartDrive) return; command = VehicleBleManager.VehicleCommand.EMOTOR_STOP; break;
            default: return;
        }
        VehicleBleManager ble = VehicleBleManager.getInstance(this);
        VehicleTelemetry latest = new TelemetryDatabaseHelper(this).getLatest();
        if (latest != null) ble.setTelemetry(latest);
        if (ble.getConnectionState() != VehicleBleManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "Watch key action rejected without BLE connection");
            return;
        }
        ble.sendCommand(command, (success, message, telemetry) -> {
            if (!success) Log.w(TAG, "Watch key action failed: " + message);
            else if (telemetry != null) {
                new TelemetryDatabaseHelper(this).insert(telemetry);
                WearSyncManager.syncTelemetry(this, telemetry);
            }
        });
    }
}
