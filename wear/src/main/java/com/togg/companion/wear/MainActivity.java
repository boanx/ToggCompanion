package com.togg.companion.wear;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Minimal Samsung Galaxy Watch (Wear OS) Companion Application.
 * Displays only the 4 critical vehicle telemetry metrics:
 * 1. High-Voltage Traction Battery (% SoC & Range)
 * 2. 12V Auxiliary Battery Level
 * 3. Tire Pressure Status (Normal vs Low Alert)
 * 4. Vehicle Location & Walking Navigation
 */
public class MainActivity extends Activity implements WatchDataStore.OnDataChangeListener {
    private static final String TAG = "WatchMainActivity";

    private TextView tvHeaderTitle;
    private TextView tvLastSync;

    // 1. Batarya & Şarj
    private TextView tvBatterySoc;
    private TextView tvRangeKm;
    private TextView tvChargingStatus;

    // 2. 12V Akü
    private TextView tv12vSoc;
    private TextView tv12vStatus;

    // 3. Lastikler
    private TextView tvTiresBadge;
    private TextView tvTiresSummary;

    // 4. Konum & Park
    private TextView tvParkAddress;
    private TextView tvDistanceBearing;
    private Button btnWalkToCar;

    // 5. Dijital Anahtar
    private TextView tvWatchKeyBadge;
    private TextView tvWatchKeyDetails;
    private View layoutWatchKeyActions;
    private Button btnWatchKeyLock;
    private Button btnWatchKeyUnlock;

    // Refresh Action
    private Button btnSyncRequest;

    private LocationManager locationManager;
    private Location lastWatchLocation = null;
    private WatchTelemetry currentTelemetry = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupListeners();
        initLocation();

        // Load cached telemetry
        currentTelemetry = WatchDataStore.getTelemetry(this);
        renderUi(currentTelemetry);
    }

    private void bindViews() {
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvLastSync = findViewById(R.id.tv_last_sync);

        tvBatterySoc = findViewById(R.id.tv_battery_soc);
        tvRangeKm = findViewById(R.id.tv_range_km);
        tvChargingStatus = findViewById(R.id.tv_charging_status);

        tv12vSoc = findViewById(R.id.tv_12v_soc);
        tv12vStatus = findViewById(R.id.tv_12v_status);

        tvTiresBadge = findViewById(R.id.tv_tires_badge);
        tvTiresSummary = findViewById(R.id.tv_tires_summary);

        tvParkAddress = findViewById(R.id.tv_park_address);
        tvDistanceBearing = findViewById(R.id.tv_distance_bearing);
        btnWalkToCar = findViewById(R.id.btn_walk_to_car);

        // 5. Dijital Anahtar
        tvWatchKeyBadge = findViewById(R.id.tv_watch_key_badge);
        tvWatchKeyDetails = findViewById(R.id.tv_watch_key_details);
        layoutWatchKeyActions = findViewById(R.id.layout_watch_key_actions);
        btnWatchKeyLock = findViewById(R.id.btn_watch_key_lock);
        btnWatchKeyUnlock = findViewById(R.id.btn_watch_key_unlock);

        btnSyncRequest = findViewById(R.id.btn_sync_request);
    }

    private void setupListeners() {
        btnWalkToCar.setOnClickListener(v -> {
            vibrate(30);
            openWalkingNavigation();
        });

        if (btnWatchKeyLock != null) {
            btnWatchKeyLock.setOnClickListener(v -> {
                vibrate(40);
                executeWatchDigitalKeyCommand("lock");
            });
        }

        if (btnWatchKeyUnlock != null) {
            btnWatchKeyUnlock.setOnClickListener(v -> {
                vibrate(40);
                executeWatchDigitalKeyCommand("unlock");
            });
        }

        btnSyncRequest.setOnClickListener(v -> {
            vibrate(40);
            requestSyncFromPhone();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        WatchDataStore.registerListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentTelemetry = WatchDataStore.getTelemetry(this);
        renderUi(currentTelemetry);
        // Automatically request latest telemetry on watch resume
        requestSyncFromPhone();
    }

    @Override
    protected void onStop() {
        super.onStop();
        WatchDataStore.unregisterListener(this);
    }

    @Override
    public void onDataChanged(WatchTelemetry telemetry) {
        runOnUiThread(() -> {
            currentTelemetry = telemetry;
            renderUi(telemetry);
        });
    }

    private void renderUi(WatchTelemetry t) {
        if (t == null) return;

        // Header
        tvLastSync.setText("● Senkronize • " + t.getRelativeTimeString());

        // 1. High-Voltage Battery & Range
        tvBatterySoc.setText("%" + t.soc);
        tvRangeKm.setText(t.rangeKm + " km menzil");
        if (t.isCharging) {
            tvBatterySoc.setTextColor(getColor(R.color.status_green));
            tvChargingStatus.setText(String.format(Locale.getDefault(), "⚡ Şarj Oluyor (%.1f kW)", t.chargePowerKw));
            tvChargingStatus.setTextColor(getColor(R.color.status_green));
        } else {
            tvBatterySoc.setTextColor(getColor(R.color.white));
            tvChargingStatus.setText("Sürüşe Hazır");
            tvChargingStatus.setTextColor(getColor(R.color.text_secondary));
        }

        // 2. 12V Auxiliary Battery
        tv12vSoc.setText("%" + t.batt12vSoc);
        if (t.batt12vAlert) {
            tv12vSoc.setTextColor(getColor(R.color.status_red));
            tv12vStatus.setText("⚠️ 12V Akü Düşük! (%60 altı)");
            tv12vStatus.setTextColor(getColor(R.color.status_red));
        } else {
            tv12vSoc.setTextColor(getColor(R.color.status_green));
            tv12vStatus.setText("Akü Seviyesi Normal");
            tv12vStatus.setTextColor(getColor(R.color.status_green));
        }

        // 3. Tire Pressure Status
        if (t.tiresNormal) {
            tvTiresBadge.setText("OK");
            tvTiresBadge.setTextColor(getColor(R.color.status_green));
            tvTiresSummary.setText(t.tiresSummary);
            tvTiresSummary.setTextColor(getColor(R.color.text_secondary));
        } else {
            tvTiresBadge.setText("UYARI");
            tvTiresBadge.setTextColor(getColor(R.color.status_red));
            tvTiresSummary.setText("⚠️ " + t.tiresSummary);
            tvTiresSummary.setTextColor(getColor(R.color.status_red));
        }

        // 4. Location & Navigation
        tvParkAddress.setText(t.parkAddress != null ? t.parkAddress : "Konum alınıyor...");
        if (lastWatchLocation != null) {
            tvDistanceBearing.setText(t.calculateDistanceString(lastWatchLocation));
        } else {
            tvDistanceBearing.setText("📍 Konum hazır (Haritada Aç)");
        }

        // 5. Dijital Anahtar Durumu
        renderDigitalKeyUi();
    }

    private void renderDigitalKeyUi() {
        WatchDataStore.WatchDigitalKey key = WatchDataStore.getDigitalKey(this);
        if (key != null && key.isValid()) {
            if (tvWatchKeyBadge != null) {
                tvWatchKeyBadge.setText("AKTİF");
                tvWatchKeyBadge.setTextColor(getColor(R.color.status_green));
            }
            if (tvWatchKeyDetails != null) {
                String details = key.plate + " • " + key.getRemainingDays();
                if (key.seId != null && !key.seId.isEmpty()) {
                    details += "\nSE-ID: " + key.seId;
                }
                tvWatchKeyDetails.setText(details);
            }
            if (layoutWatchKeyActions != null) {
                layoutWatchKeyActions.setVisibility(View.VISIBLE);
            }
            if (btnWatchKeyLock != null) {
                btnWatchKeyLock.setEnabled(key.canLock);
            }
            if (btnWatchKeyUnlock != null) {
                btnWatchKeyUnlock.setEnabled(key.canUnlock);
            }
        } else if (key != null && !key.isValid()) {
            if (tvWatchKeyBadge != null) {
                tvWatchKeyBadge.setText("SÜRESİ DOLDU");
                tvWatchKeyBadge.setTextColor(getColor(R.color.status_red));
            }
            if (tvWatchKeyDetails != null) {
                tvWatchKeyDetails.setText("Anahtarın kullanım süresi dolmuş");
            }
            if (layoutWatchKeyActions != null) {
                layoutWatchKeyActions.setVisibility(View.GONE);
            }
        } else {
            if (tvWatchKeyBadge != null) {
                tvWatchKeyBadge.setText("YOK");
                tvWatchKeyBadge.setTextColor(getColor(R.color.text_muted));
            }
            if (tvWatchKeyDetails != null) {
                tvWatchKeyDetails.setText("Anahtar telefondan senkronize edilmedi");
            }
            if (layoutWatchKeyActions != null) {
                layoutWatchKeyActions.setVisibility(View.GONE);
            }
        }
    }

    private void executeWatchDigitalKeyCommand(String cmd) {
        WatchDataStore.WatchDigitalKey key = WatchDataStore.getDigitalKey(this);
        if (key == null || !key.isValid()) {
            Toast.makeText(this, "Geçerli dijital anahtar yok!", Toast.LENGTH_SHORT).show();
            return;
        }

        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes != null && !nodes.isEmpty()) {
                byte[] payload = cmd.getBytes(StandardCharsets.UTF_8);
                for (Node node : nodes) {
                    Wearable.getMessageClient(this).sendMessage(node.getId(), "/execute_key_action", payload);
                }
                String actionName = "lock".equalsIgnoreCase(cmd) ? "🔒 Kilitleme" : "🔓 Açma";
                Toast.makeText(this, actionName + " telefona iletildi", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Telefon bağlı değil (Bluetooth)", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Komut iletilemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void requestSyncFromPhone() {
        btnSyncRequest.setText("🔄 İsteniyor...");
        btnSyncRequest.setEnabled(false);

        Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes != null && !nodes.isEmpty()) {
                byte[] payload = "request".getBytes(StandardCharsets.UTF_8);
                for (Node node : nodes) {
                    Wearable.getMessageClient(this).sendMessage(node.getId(), "/request_telemetry", payload);
                    Wearable.getMessageClient(this).sendMessage(node.getId(), "/request_digital_key", payload);
                }
                Toast.makeText(this, "Telefondan veri isteniyor...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Telefon bağlı değil (Bluetooth)", Toast.LENGTH_SHORT).show();
            }
            btnSyncRequest.postDelayed(() -> {
                btnSyncRequest.setText("🔄 Telefondan Güncelle");
                btnSyncRequest.setEnabled(true);
            }, 1500);
        }).addOnFailureListener(e -> {
            btnSyncRequest.setText("🔄 Telefondan Güncelle");
            btnSyncRequest.setEnabled(true);
        });
    }

    private void openWalkingNavigation() {
        if (currentTelemetry == null || (currentTelemetry.latitude == 0.0 && currentTelemetry.longitude == 0.0)) {
            Toast.makeText(this, "Araç konumu henüz alınamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        String geoUri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(Togg+T10F)",
                currentTelemetry.latitude, currentTelemetry.longitude,
                currentTelemetry.latitude, currentTelemetry.longitude);

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Fallback to Google walking navigation intent
            String walkNavUri = String.format(Locale.US, "google.navigation:q=%f,%f&mode=w",
                    currentTelemetry.latitude, currentTelemetry.longitude);
            Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(walkNavUri));
            try {
                startActivity(navIntent);
            } catch (Exception e2) {
                Toast.makeText(this, "Harita uygulaması açılamadı", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locationManager != null) {
                    Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnown == null) {
                        lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                    if (lastKnown != null) {
                        lastWatchLocation = lastKnown;
                    }
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
                }
            } catch (Exception e) {
                Log.w(TAG, "Location error: " + e.getMessage());
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            lastWatchLocation = location;
            if (currentTelemetry != null) {
                tvDistanceBearing.setText(currentTelemetry.calculateDistanceString(lastWatchLocation));
            }
        }
    };

    private void vibrate(long millis) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(millis);
                }
            }
        } catch (Exception ignored) {}
    }
}
