package com.togg.companion.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.core.TrumoreBridge;
import com.togg.companion.core.VehicleTelemetry;

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
        }

        if (cmd == null) {
            cmd = "refresh";
        }

        executeCommand(cmd.toLowerCase());
    }

    private void executeCommand(String cmd) {
        SecureTokenStorage storage = new SecureTokenStorage(this);
        TelemetryDatabaseHelper dbHelper = new TelemetryDatabaseHelper(this);
        VehicleTelemetry latest = dbHelper.getLatest();

        switch (cmd) {
            case "lock":
                if (!storage.isDemoMode() && storage.hasValidToken()) {
                    Toast.makeText(this, "Togg T10F: Trumore Akıllı Erişim açılıyor...", Toast.LENGTH_SHORT).show();
                    TrumoreBridge.openDoorControl(this);
                } else {
                    Toast.makeText(this, "Togg T10F: Kapılar Kilitlendi 🔒", Toast.LENGTH_SHORT).show();
                    if (latest != null) {
                        dbHelper.insert(latest.withLock(true));
                    }
                }
                break;

            case "unlock":
                if (!storage.isDemoMode() && storage.hasValidToken()) {
                    Toast.makeText(this, "Togg T10F: Trumore Akıllı Erişim açılıyor...", Toast.LENGTH_SHORT).show();
                    TrumoreBridge.openDoorControl(this);
                } else {
                    Toast.makeText(this, "Togg T10F: Kapı Kilitleri Açıldı 🔓", Toast.LENGTH_SHORT).show();
                    if (latest != null) {
                        dbHelper.insert(latest.withLock(false));
                    }
                }
                break;

            case "climate_start":
                if (!storage.isDemoMode() && storage.hasValidToken()) {
                    Toast.makeText(this, "Togg T10F: Trumore İklimlendirme açılıyor...", Toast.LENGTH_SHORT).show();
                    TrumoreBridge.openClimateControl(this);
                } else {
                    Toast.makeText(this, "Togg T10F: Ön İklimlendirme Başlatıldı (22°C) ❄️", Toast.LENGTH_SHORT).show();
                    if (latest != null) {
                        dbHelper.insert(latest.withClimate(22.0, true, false));
                    }
                }
                break;

            case "climate_stop":
                if (!storage.isDemoMode() && storage.hasValidToken()) {
                    TrumoreBridge.openClimateControl(this);
                } else {
                    Toast.makeText(this, "Togg T10F: İklimlendirme Kapatıldı", Toast.LENGTH_SHORT).show();
                    if (latest != null) {
                        dbHelper.insert(latest.withClimate(latest.targetHvacCelsius, false, false));
                    }
                }
                break;

            case "defrost":
                Toast.makeText(this, "Togg T10F: Ön Cam Rezistansı & Buğu Çözme Açıldı", Toast.LENGTH_SHORT).show();
                if (latest != null) {
                    dbHelper.insert(latest.withClimate(latest.targetHvacCelsius, true, true));
                }
                break;

            case "trunk":
                if (!storage.isDemoMode() && storage.hasValidToken()) {
                    TrumoreBridge.openTrunkControl(this);
                } else {
                    boolean nowOpen = latest == null || !latest.trunkOpen;
                    Toast.makeText(this, nowOpen ? "Togg T10F: Bagaj Kapağı Açıldı" : "Togg T10F: Bagaj Kapağı Kapatıldı", Toast.LENGTH_SHORT).show();
                    if (latest != null) {
                        dbHelper.insert(latest.withTrunk(nowOpen));
                    }
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
