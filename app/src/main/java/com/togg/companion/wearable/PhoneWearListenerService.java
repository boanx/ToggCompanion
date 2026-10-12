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
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.ui.MainActivity;
import org.json.JSONObject;

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
            SecureTokenStorage storage = new SecureTokenStorage(this);
            new TvaApiClient(storage).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
                @Override public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                    if (telemetry != null && (isLive || storage.isDemoMode())) {
                        storage.saveActiveTelemetry(telemetry);
                        new TelemetryDatabaseHelper(PhoneWearListenerService.this).insert(telemetry);
                        WearSyncManager.syncTelemetry(PhoneWearListenerService.this, telemetry);
                        Log.d(TAG, "Sent freshly fetched telemetry to watch; live=" + isLive);
                        return;
                    }
                    syncCachedTelemetry();
                }
                @Override public void onError(String message, VehicleTelemetry fallback) {
                    Log.w(TAG, "Live watch refresh failed: " + message);
                    syncCachedTelemetry();
                }
            });
        } else if (messageEvent.getPath().equals(WearSyncManager.PATH_REQUEST_KEY)) {
            Log.d(TAG, "Watch requested digital key sync");
            syncAccessMetadata();
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
                         dispatchVerifiedKeyAction(data, sourceNodeId);
                    })
                    .addOnFailureListener(error -> Log.e(TAG, "Unable to verify watch sender node", error));
        }
    }

    private void syncCachedTelemetry() {
        VehicleTelemetry telemetry = null;
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) telemetry = activity.getCurrentTelemetry();
        if (telemetry == null) telemetry = new TelemetryDatabaseHelper(this).getLatest();
        if (telemetry != null) {
            Log.d(TAG, "Sending cached telemetry to watch from " + telemetry.timestampMillis);
            WearSyncManager.syncTelemetry(this, telemetry);
        } else {
            Log.w(TAG, "No telemetry available to send to watch");
        }
    }

    private static boolean isValidKeyAction(String action) {
        switch (action) {
            case "lock": case "unlock": case "trunk": case "trunk_open": case "trunk_close": case "emotor_start":
            case "emotor_stop": case "refresh": return true;
            default: return false;
        }
    }

    private void dispatchVerifiedKeyAction(String action, String sourceNodeId) {
        if ("refresh".equals(action)) {
            syncAccessMetadata();
            syncCachedTelemetry();
            sendKeyActionResult(sourceNodeId, true, "Senkronizasyon isteği telefona ulaştı.");
            return;
        }
        VehicleBleManager.VehicleCommand command;
        switch (action) {
            case "lock": command = VehicleBleManager.VehicleCommand.LOCK; break;
            case "unlock": command = VehicleBleManager.VehicleCommand.UNLOCK; break;
            case "trunk": command = VehicleBleManager.VehicleCommand.TRUNK; break;
            case "trunk_open": command = VehicleBleManager.VehicleCommand.TRUNK_OPEN; break;
            case "trunk_close": command = VehicleBleManager.VehicleCommand.TRUNK_CLOSE; break;
            case "emotor_start": command = VehicleBleManager.VehicleCommand.EMOTOR_START; break;
            case "emotor_stop": command = VehicleBleManager.VehicleCommand.EMOTOR_STOP; break;
            default: return;
        }
        VehicleBleManager ble = VehicleBleManager.getInstance(this);
        VehicleTelemetry latest = null;
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) latest = activity.getCurrentTelemetry();
        if (latest == null) latest = new TelemetryDatabaseHelper(this).getLatest();
        if (latest != null) ble.setTelemetry(latest);
        ble.sendCommand(command, (success, message, telemetry) -> {
            if (!success) Log.w(TAG, "Watch key action failed: " + message);
            else if (telemetry != null) {
                TelemetryDatabaseHelper db = new TelemetryDatabaseHelper(this);
                db.insert(telemetry);
                new SecureTokenStorage(this).saveActiveTelemetry(telemetry);
                WearSyncManager.syncTelemetry(this, telemetry);
                MainActivity currentActivity = MainActivity.getInstance();
                if (currentActivity != null) currentActivity.onVehicleCommandCompleted(telemetry);
            }
            sendKeyActionResult(sourceNodeId, success, message);
        });
    }

    private void syncAccessMetadata() {
        VehicleBleManager ble = VehicleBleManager.getInstance(this);
        DigitalKey metadata = ble.isDemoConnected() ? new DigitalKeyManager(this).getActiveKey()
                : ble.getSmartAccessKeyMetadata();
        WearSyncManager.syncDigitalKey(this, metadata);
    }

    private void sendKeyActionResult(String nodeId, boolean success, String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", success);
            result.put("message", message == null ? "Komut sonucu alınamadı." : message);
            Wearable.getMessageClient(this).sendMessage(nodeId, WearSyncManager.PATH_KEY_ACTION_RESULT,
                    result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception error) {
            Log.w(TAG, "Unable to send watch action result", error);
        }
    }
}
