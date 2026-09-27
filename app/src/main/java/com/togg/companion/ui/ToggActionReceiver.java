package com.togg.companion.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.core.VehicleTelemetry;

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

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        TelemetryDatabaseHelper dbHelper = new TelemetryDatabaseHelper(context);
        VehicleTelemetry latest = dbHelper.getLatest();

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
        }
    }
}
