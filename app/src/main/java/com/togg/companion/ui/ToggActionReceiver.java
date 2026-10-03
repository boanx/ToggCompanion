package com.togg.companion.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.core.VehicleBleManager;
import com.togg.companion.core.DigitalKey;
import com.togg.companion.core.DigitalKeyManager;
import com.togg.companion.wearable.WearSyncManager;

/**
 * BroadcastReceiver for headless automation triggers from Samsung Modes and Routines,
 * Tasker, Automate, or ADB.
 */
public class ToggActionReceiver extends BroadcastReceiver {

    public static final String ACTION_LOCK = "com.togg.companion.ACTION_LOCK";
    public static final String ACTION_UNLOCK = "com.togg.companion.ACTION_UNLOCK";
    public static final String ACTION_CLIMATE_START = "com.togg.companion.ACTION_CLIMATE_START";
    public static final String ACTION_CLIMATE_STOP = "com.togg.companion.ACTION_CLIMATE_STOP";
    public static final String ACTION_DEFROST = "com.togg.companion.ACTION_DEFROST";
    public static final String ACTION_TRUNK_TOGGLE = "com.togg.companion.ACTION_TRUNK_TOGGLE";
    public static final String ACTION_DRIVE_MODE_ECO = "com.togg.companion.ACTION_DRIVE_MODE_ECO";
    public static final String ACTION_DRIVE_MODE_COMFORT = "com.togg.companion.ACTION_DRIVE_MODE_COMFORT";
    public static final String ACTION_DRIVE_MODE_SPORT = "com.togg.companion.ACTION_DRIVE_MODE_SPORT";
    public static final String ACTION_DRIVE_MODE_NEXT = "com.togg.companion.ACTION_DRIVE_MODE_NEXT";
    public static final String ACTION_EMOTOR_TOGGLE = "com.togg.companion.ACTION_EMOTOR_TOGGLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String command = intent.getStringExtra("action_command");
        if (command != null) action = command.toLowerCase(java.util.Locale.ROOT);
        if (action == null) return;

        TelemetryDatabaseHelper dbHelper = new TelemetryDatabaseHelper(context);
        VehicleTelemetry latest = dbHelper.getLatest();

        VehicleBleManager.VehicleCommand bleCommand = null;
        if (ACTION_LOCK.equals(action) || "lock".equals(action)) bleCommand = VehicleBleManager.VehicleCommand.LOCK;
        else if (ACTION_UNLOCK.equals(action) || "unlock".equals(action)) bleCommand = VehicleBleManager.VehicleCommand.UNLOCK;
        else if (ACTION_TRUNK_TOGGLE.equals(action) || "trunk".equals(action)) bleCommand = VehicleBleManager.VehicleCommand.TRUNK;
        else if ("emotor".equals(action) || "emotor_start".equals(action) || "emotor_stop".equals(action)
                || "start_drive".equals(action) || ACTION_EMOTOR_TOGGLE.equals(action)) {
            boolean ready = "emotor_start".equals(action) || "start_drive".equals(action)
                    || (("emotor".equals(action) || ACTION_EMOTOR_TOGGLE.equals(action)) && latest != null && !latest.isReadyToDrive);
            DigitalKey key = new DigitalKeyManager(context).getActiveKey();
            if (ready && key != null && !key.canStartDrive) {
                Toast.makeText(context, "Bu dijital anahtarın aracı çalıştırma yetkisi yok.", Toast.LENGTH_LONG).show();
                return;
            }
            bleCommand = ready ? VehicleBleManager.VehicleCommand.EMOTOR_START : VehicleBleManager.VehicleCommand.EMOTOR_STOP;
        }
        if (bleCommand != null) {
            VehicleBleManager manager = VehicleBleManager.getInstance(context);
            manager.setTelemetry(latest);
            PendingResult pending = goAsync();
            manager.sendCommand(bleCommand, (success, message, updated) -> {
                try {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    if (success && updated != null) {
                        dbHelper.insert(updated);
                        WearSyncManager.syncTelemetry(context, updated);
                    }
                } finally { pending.finish(); }
            });
            return;
        }

        if ("emotor".equals(action) || "emotor_start".equals(action) || "emotor_stop".equals(action)
                || "start_drive".equals(action) || ACTION_EMOTOR_TOGGLE.equals(action)) {
            DigitalKey activeKey = new DigitalKeyManager(context).getActiveKey();
            boolean ready = "emotor_start".equals(action) || "start_drive".equals(action)
                    || (("emotor".equals(action) || ACTION_EMOTOR_TOGGLE.equals(action)) && latest != null && !latest.isReadyToDrive);
            if (ready && activeKey != null && !activeKey.canStartDrive) {
                Toast.makeText(context, "Bu dijital anahtarın aracı çalıştırma yetkisi yok.", Toast.LENGTH_LONG).show();
                return;
            }
            if (latest != null) {
                latest = latest.withReadyToDrive(ready);
                dbHelper.insert(latest);
                WearSyncManager.syncTelemetry(context, latest);
                Toast.makeText(context, ready ? "Togg T10F: e-Motor Aktif! Araç Sürüşe Hazır (READY) ⚡" : "Togg T10F: e-Motor Kapatıldı", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        switch (action) {
            case ACTION_LOCK:
                Toast.makeText(context, "Togg T10F: Kapılar Kilitlendi (Rutin) 🔒", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withLock(true));
                }
                break;

            case ACTION_UNLOCK:
                Toast.makeText(context, "Togg T10F: Kapı Kilitleri Açıldı (Rutin) 🔓", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withLock(false));
                }
                break;

            case ACTION_CLIMATE_START:
                Toast.makeText(context, "Togg T10F: Ön İklimlendirme Başlatıldı ❄️", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withClimate(22.0, true, false));
                }
                break;

            case ACTION_CLIMATE_STOP:
                Toast.makeText(context, "Togg T10F: İklimlendirme Durduruldu", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withClimate(latest.targetHvacCelsius, false, false));
                }
                break;

            case ACTION_DEFROST:
                Toast.makeText(context, "Togg T10F: Ön Cam Rezistansı Açıldı", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withClimate(latest.targetHvacCelsius, true, true));
                }
                break;

            case ACTION_TRUNK_TOGGLE:
                boolean nowOpen = latest == null || !latest.trunkOpen;
                Toast.makeText(context, nowOpen ? "Togg T10F: Bagaj Kapağı Açıldı" : "Togg T10F: Bagaj Kapağı Kapatıldı", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withTrunk(nowOpen));
                }
                break;

            case ACTION_DRIVE_MODE_ECO:
            case ACTION_DRIVE_MODE_COMFORT:
            case ACTION_DRIVE_MODE_SPORT:
            case ACTION_DRIVE_MODE_NEXT:
            case "drive_mode_eco":
            case "drive_mode_comfort":
            case "drive_mode_sport":
            case "drive_mode_next":
                if (latest != null) {
                    String mode = ACTION_DRIVE_MODE_ECO.equals(action) || "drive_mode_eco".equals(action) ? "Eco"
                            : ACTION_DRIVE_MODE_SPORT.equals(action) || "drive_mode_sport".equals(action) ? "Sport"
                            : ACTION_DRIVE_MODE_NEXT.equals(action) || "drive_mode_next".equals(action)
                                ? ("Eco".equalsIgnoreCase(latest.driveMode) ? "Comfort" : ("Comfort".equalsIgnoreCase(latest.driveMode) ? "Sport" : "Eco"))
                                : "Comfort";
                    latest = latest.withDriveMode(mode);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(context, latest);
                    Toast.makeText(context, "Togg T10F: Sürüş Modu -> " + ("Sport".equals(mode) ? "Spor ⚡" : ("Comfort".equals(mode) ? "Konfor" : "Eco")), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
