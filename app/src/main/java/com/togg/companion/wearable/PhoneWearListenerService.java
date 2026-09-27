package com.togg.companion.wearable;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
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
        }
    }
}
