package com.togg.companion.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.core.DigitalKey;
import com.togg.companion.core.DigitalKeyManager;
import com.togg.companion.core.TrumoreBridge;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.core.VehicleBleManager;
import com.togg.companion.wearable.WearSyncManager;

/**
 * Headless translucent action activity for Samsung Modes &amp; Routines, Bixby, and App Shortcuts.
 * Executes vehicle commands directly and closes cleanly without leaving open windows.
 */
public class ToggActionActivity extends Activity {

    public static final String ACTION_LOCK = "com.togg.companion.ACTION_LOCK";
    public static final String ACTION_UNLOCK = "com.togg.companion.ACTION_UNLOCK";
    public static final String ACTION_CLIMATE_START = "com.togg.companion.ACTION_CLIMATE_START";
    public static final String ACTION_CLIMATE_STOP = "com.togg.companion.ACTION_CLIMATE_STOP";
    public static final String ACTION_DEFROST = "com.togg.companion.ACTION_DEFROST";
    public static final String ACTION_TRUNK_TOGGLE = "com.togg.companion.ACTION_TRUNK_TOGGLE";
    public static final String ACTION_LOCATE = "com.togg.companion.ACTION_LOCATE";
    public static final String ACTION_REFRESH = "com.togg.companion.ACTION_REFRESH";
    public static final String ACTION_DRIVE_MODE_ECO = "com.togg.companion.ACTION_DRIVE_MODE_ECO";
    public static final String ACTION_DRIVE_MODE_COMFORT = "com.togg.companion.ACTION_DRIVE_MODE_COMFORT";
    public static final String ACTION_DRIVE_MODE_SPORT = "com.togg.companion.ACTION_DRIVE_MODE_SPORT";
    public static final String ACTION_DRIVE_MODE_NEXT = "com.togg.companion.ACTION_DRIVE_MODE_NEXT";
    public static final String ACTION_EMOTOR_TOGGLE = "com.togg.companion.ACTION_EMOTOR_TOGGLE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        finish();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        String cmd = intent.getStringExtra("action_command");

        // Support deep links: togg://action?cmd=lock
        Uri data = intent.getData();
        if (data != null && "togg".equalsIgnoreCase(data.getScheme())) {
            String queryCmd = data.getQueryParameter("cmd");
            if (queryCmd != null) {
                cmd = queryCmd;
            }
        }

        if (cmd == null && action != null) {
            if (ACTION_LOCK.equals(action)) cmd = "lock";
            else if (ACTION_UNLOCK.equals(action)) cmd = "unlock";
            else if (ACTION_CLIMATE_START.equals(action)) cmd = "climate_start";
            else if (ACTION_CLIMATE_STOP.equals(action)) cmd = "climate_stop";
            else if (ACTION_DEFROST.equals(action)) cmd = "defrost";
            else if (ACTION_TRUNK_TOGGLE.equals(action)) cmd = "trunk";
            else if (ACTION_LOCATE.equals(action)) cmd = "locate";
            else if (ACTION_REFRESH.equals(action)) cmd = "refresh";
            else if (ACTION_DRIVE_MODE_ECO.equals(action)) cmd = "drive_mode_eco";
            else if (ACTION_DRIVE_MODE_COMFORT.equals(action)) cmd = "drive_mode_comfort";
            else if (ACTION_DRIVE_MODE_SPORT.equals(action)) cmd = "drive_mode_sport";
            else if (ACTION_DRIVE_MODE_NEXT.equals(action)) cmd = "drive_mode_next";
            else if (ACTION_EMOTOR_TOGGLE.equals(action)) cmd = "emotor";
        }

        if (cmd == null) {
            cmd = "refresh";
        }

        if (isProtectedVehicleCommand(cmd) && data != null
                && "togg".equalsIgnoreCase(data.getScheme())) {
            // Vehicle actions are accepted only through the signature-protected component
            // action path; the old browsable togg://action path is deliberately rejected.
            Toast.makeText(this, "Araç komutu doğrulanamadı.", Toast.LENGTH_LONG).show();
            return;
        }

        executeCommand(cmd.toLowerCase());
    }

    private boolean isProtectedVehicleCommand(String cmd) {
        if (cmd == null) return false;
        String normalized = cmd.toLowerCase(java.util.Locale.ROOT);
        return "lock".equals(normalized) || "unlock".equals(normalized)
                || "trunk".equals(normalized) || "trunk_toggle".equals(normalized)
                || "emotor".equals(normalized) || "emotor_start".equals(normalized)
                || "emotor_stop".equals(normalized) || "start_drive".equals(normalized);
    }

    private void executeCommand(String cmd) {
        SecureTokenStorage storage = new SecureTokenStorage(this);
        TelemetryDatabaseHelper dbHelper = new TelemetryDatabaseHelper(this);
        VehicleTelemetry latest = dbHelper.getLatest();
        DigitalKeyManager keyMgr = new DigitalKeyManager(this);
        DigitalKey activeKey = keyMgr.getActiveKey();
        String keyTag = (activeKey != null && activeKey.isValid()) ? " [🔑 " + activeKey.seId + "]" : "";

        VehicleBleManager.VehicleCommand bleCommand = null;
        if ("lock".equals(cmd)) bleCommand = VehicleBleManager.VehicleCommand.LOCK;
        else if ("unlock".equals(cmd)) bleCommand = VehicleBleManager.VehicleCommand.UNLOCK;
        else if ("trunk".equals(cmd) || "trunk_toggle".equals(cmd)) bleCommand = VehicleBleManager.VehicleCommand.TRUNK;
        else if ("emotor".equals(cmd) || "emotor_start".equals(cmd) || "emotor_stop".equals(cmd) || "start_drive".equals(cmd)) {
            boolean ready = "emotor_start".equals(cmd) || "start_drive".equals(cmd)
                    || ("emotor".equals(cmd) && latest != null && !latest.isReadyToDrive);
            if (ready && activeKey != null && !activeKey.canStartDrive) {
                Toast.makeText(this, "Bu dijital anahtarın aracı çalıştırma yetkisi yok.", Toast.LENGTH_LONG).show();
                return;
            }
            bleCommand = ready ? VehicleBleManager.VehicleCommand.EMOTOR_START : VehicleBleManager.VehicleCommand.EMOTOR_STOP;
        }
        if (bleCommand != null) {
            VehicleBleManager manager = VehicleBleManager.getInstance(this);
            manager.setTelemetry(latest);
            manager.sendCommand(bleCommand, (success, message, updated) -> {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                if (success && updated != null) {
                    dbHelper.insert(updated);
                    WearSyncManager.syncTelemetry(getApplicationContext(), updated);
                }
            });
            return;
        }

        switch (cmd) {
            case "emotor": case "emotor_start": case "emotor_stop": case "start_drive":
                boolean ready = "emotor_start".equals(cmd) || "start_drive".equals(cmd)
                        || ("emotor".equals(cmd) && latest != null && !latest.isReadyToDrive);
                if (ready && activeKey != null && !activeKey.canStartDrive) {
                    Toast.makeText(this, "Bu dijital anahtarın aracı çalıştırma yetkisi yok.", Toast.LENGTH_LONG).show();
                    break;
                }
                if (latest != null) {
                    latest = latest.withReadyToDrive(ready);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                    Toast.makeText(this, ready ? "Togg T10F: e-Motor Aktif! Araç Sürüşe Hazır (READY) ⚡" : "Togg T10F: e-Motor Kapatıldı", Toast.LENGTH_SHORT).show();
                }
                break;
            case "lock":
                Toast.makeText(this, "Togg T10F: Kapılar Kilitlendi 🔒" + keyTag, Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    latest = latest.withLock(true);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                }
                break;

            case "unlock":
                Toast.makeText(this, "Togg T10F: Kapı Kilitleri Açıldı 🔓" + keyTag, Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    latest = latest.withLock(false);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                }
                break;

            case "climate_start":
                Toast.makeText(this, "Togg T10F: Ön İklimlendirme Başlatıldı (22°C) ❄️" + keyTag, Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    latest = latest.withClimate(22.0, true, false);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                }
                break;

            case "climate_stop":
                Toast.makeText(this, "Togg T10F: İklimlendirme Kapatıldı" + keyTag, Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    latest = latest.withClimate(latest.targetHvacCelsius, false, false);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                }
                break;

            case "defrost":
                Toast.makeText(this, "Togg T10F: Ön Cam Rezistansı & Buğu Çözme Açıldı ♨️" + keyTag, Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    latest = latest.withClimate(latest.targetHvacCelsius, true, true);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                }
                break;

            case "trunk":
                boolean nowOpen = latest == null || !latest.trunkOpen;
                Toast.makeText(this, nowOpen ? ("Togg T10F: Bagaj Kapağı Açıldı 📦" + keyTag) : ("Togg T10F: Bagaj Kapağı Kapatıldı" + keyTag), Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    latest = latest.withTrunk(nowOpen);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                }
                break;

            case "drive_mode_eco":
            case "drive_mode_comfort":
            case "drive_mode_sport":
            case "drive_mode_next":
                if (latest != null) {
                    String mode;
                    if ("drive_mode_eco".equals(cmd)) mode = "Eco";
                    else if ("drive_mode_sport".equals(cmd)) mode = "Sport";
                    else if ("drive_mode_next".equals(cmd)) mode = "Eco".equalsIgnoreCase(latest.driveMode) ? "Comfort" : ("Comfort".equalsIgnoreCase(latest.driveMode) ? "Sport" : "Eco");
                    else mode = "Comfort";
                    latest = latest.withDriveMode(mode);
                    dbHelper.insert(latest);
                    WearSyncManager.syncTelemetry(this, latest);
                    Toast.makeText(this, "Togg T10F: Sürüş Modu -> " + ("Sport".equals(mode) ? "Spor ⚡" : ("Comfort".equals(mode) ? "Konfor" : "Eco")), Toast.LENGTH_SHORT).show();
                }
                break;

            case "locate":
                double lat = latest != null ? latest.latitude : 39.925054;
                double lon = latest != null ? latest.longitude : 32.834755;
                Uri mapUri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(Togg T10F Park Yeri)");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                try {
                    startActivity(mapIntent);
                } catch (Exception e) {
                    Intent webMaps = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon));
                    startActivity(webMaps);
                }
                break;

            case "refresh":
            default:
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                mainIntent.putExtra("auto_refresh", true);
                startActivity(mainIntent);
                break;
        }
    }
}
