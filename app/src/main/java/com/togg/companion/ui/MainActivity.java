package com.togg.companion.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.location.Address;
import android.location.Geocoder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.togg.companion.R;
import com.togg.companion.core.DigitalKey;
import com.togg.companion.core.DigitalKeyManager;
import com.togg.companion.core.TrumoreBridge;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.wearable.WearSyncManager;

public final class MainActivity extends Activity {

    private static MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

    public VehicleTelemetry getCurrentTelemetry() {
        return currentTelemetry;
    }

    private SecureTokenStorage storage;
    private TelemetryDatabaseHelper dbHelper;
    private TvaApiClient apiClient;

    private SwipeRefreshLayout swipeRefresh;
    private ScrollView scrollRoot;
    private HorizontalScrollView tabScrollView;
    private View statusBarSpacer;
    private View navBarSpacer;

    // Header Views
    private TextView tvVehicleTitle;
    private TextView tvVehiclePlate;
    private TextView tvVehicleVin;
    private TextView tvSyncStatus;

    // 8 Tabs Navigation Elements
    private TextView[] tabButtons;
    private View[] tabPages;
    private int selectedTabIndex = 0;

    // Page 0: Ana Sayfa
    private LinearLayout homeCardsContainer;
    private TextView btnCustomizeHome;
    private TextView tvHomeSummary;

    // Page 1: Batarya & Şarj
    private TextView tvBatterySoc;
    private TextView tvRangeKm;
    private TextView tvChargingStatus;
    private TextView tvChargeMode;
    private TextView tvChargePower;
    private TextView tvBatteryCapacity;
    private TextView tvLastChargeKwh;

    // Page 2: 12V Akü & Sistem
    private TextView tv12vSoc;
    private TextView tv12vStatus;
    private TextView tv12vVolts;

    // Page 3: Lastikler & TPMS
    private TextView tvTpFL;
    private TextView tvTpFR;
    private TextView tvTpRL;
    private TextView tvTpRR;

    // Page 4: İklimlendirme / HVAC
    private TextView tvCabinTemp;
    private TextView tvTargetHvac;
    private TextView tvOutdoorTemp;
    private TextView tvClimateStatus;
    private TextView tvControlTargetTemp;
    private Button btnTempMinus;
    private Button btnTempPlus;
    private Button btnClimateToggle;
    private Button btnDefrost;

    // Page 5: Kapılar & Güvenlik
    private TextView tvDoorStatus;
    private Button btnDoorToggle;
    private TextView tvTrunkStatus;
    private Button btnTrunkToggle;

    // Page 5: Dijital Anahtar Views
    private TextView tvPhoneKeyBadge;
    private TextView tvPhoneKeyInfo;
    private Button btnCopyKeyToWatch;
    private Button btnManageDigitalKey;

    // Page 6: Sürüş İstatistikleri
    private TextView tvDriveMode;
    private TextView tvOdometer;
    private TextView tvTripDist;
    private TextView tvTripDur;
    private TextView tvTripConsumption;
    private TextView tvTripSpeed;
    private TextView btnTripCurrent;
    private TextView btnTripCharge;
    private TextView btnTripMy;
    private int selectedTripType = 0; // 0 = current, 1 = lastCharge, 2 = myTrip

    // Page 7: Konum & Park
    private TextView tvParkAddress;
    private TextView tvGpsCoords;
    private TextView tvParkDuration;
    private Button btnWalkToCar;

    // Global Actions
    private Button btnRefresh;

    // Interactive State
    private boolean isDoorLocked = true;
    private boolean isTrunkOpen = false;
    private boolean isClimateActive = false;
    private boolean isDefrostActive = false;
    private double currentTargetTemp = 21.5;
    private VehicleTelemetry currentTelemetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);

        storage = new SecureTokenStorage(this);
        dbHelper = new TelemetryDatabaseHelper(this);
        apiClient = new TvaApiClient(storage);

        bindViews();
        setupWindowInsets();
        setupSwipeRefresh();
        setupListeners();

        // Default to Tab 0 (Ana Sayfa)
        selectTab(0);

        // Load cached telemetry if present, otherwise fetch fresh/demo data
        VehicleTelemetry cached = dbHelper.getLatest();
        if (cached != null) {
            currentTelemetry = cached;
            renderTelemetry(cached, storage.isDemoMode());
        }
        refreshTelemetry();

        handleActionIntent(getIntent());
        handleDigitalKeyIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleActionIntent(intent);
        handleDigitalKeyIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDigitalKeyCard();
    }

    private void bindViews() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        scrollRoot = findViewById(R.id.scroll_root);
        tabScrollView = findViewById(R.id.tab_scroll_view);
        statusBarSpacer = findViewById(R.id.status_bar_spacer);
        navBarSpacer = findViewById(R.id.nav_bar_spacer);

        tvVehicleTitle = findViewById(R.id.tv_vehicle_title);
        tvVehiclePlate = findViewById(R.id.tv_vehicle_plate);
        tvVehicleVin = findViewById(R.id.tv_vehicle_vin);
        tvSyncStatus = findViewById(R.id.tv_sync_status);

        // 8 Tab Selector Buttons (Fixed at Top)
        tabButtons = new TextView[]{
                findViewById(R.id.tab_home),
                findViewById(R.id.tab_battery),
                findViewById(R.id.tab_aux_battery),
                findViewById(R.id.tab_tires),
                findViewById(R.id.tab_climate),
                findViewById(R.id.tab_security),
                findViewById(R.id.tab_drive),
                findViewById(R.id.tab_location)
        };

        // 8 Corresponding Page Containers
        tabPages = new View[]{
                findViewById(R.id.page_home),
                findViewById(R.id.page_battery),
                findViewById(R.id.page_aux_battery),
                findViewById(R.id.page_tires),
                findViewById(R.id.page_climate),
                findViewById(R.id.page_security),
                findViewById(R.id.page_drive),
                findViewById(R.id.page_location)
        };

        // Page 0: Ana Sayfa
        homeCardsContainer = findViewById(R.id.home_cards_container);
        btnCustomizeHome = findViewById(R.id.btn_customize_home);

        // Page 1: Batarya & Şarj
        tvBatterySoc = findViewById(R.id.tv_battery_soc);
        tvRangeKm = findViewById(R.id.tv_range_km);
        tvChargingStatus = findViewById(R.id.tv_charging_status);
        tvChargeMode = findViewById(R.id.tv_charge_mode);
        tvChargePower = findViewById(R.id.tv_charge_power);
        tvBatteryCapacity = findViewById(R.id.tv_battery_capacity);
        tvLastChargeKwh = findViewById(R.id.tv_last_charge_kwh);

        // Page 2: 12V Akü & Sistem
        tv12vSoc = findViewById(R.id.tv_12v_soc);
        tv12vStatus = findViewById(R.id.tv_12v_status);
        tv12vVolts = findViewById(R.id.tv_12v_volts);

        // Page 3: Lastikler & TPMS
        tvTpFL = findViewById(R.id.tv_tp_fl);
        tvTpFR = findViewById(R.id.tv_tp_fr);
        tvTpRL = findViewById(R.id.tv_tp_rl);
        tvTpRR = findViewById(R.id.tv_tp_rr);

        // Page 4: İklimlendirme / HVAC
        tvCabinTemp = findViewById(R.id.tv_cabin_temp);
        tvTargetHvac = findViewById(R.id.tv_target_hvac);
        tvOutdoorTemp = findViewById(R.id.tv_outdoor_temp);
        tvClimateStatus = findViewById(R.id.tv_climate_status);
        tvControlTargetTemp = findViewById(R.id.tv_control_target_temp);
        btnTempMinus = findViewById(R.id.btn_temp_minus);
        btnTempPlus = findViewById(R.id.btn_temp_plus);
        btnClimateToggle = findViewById(R.id.btn_climate_toggle);
        btnDefrost = findViewById(R.id.btn_defrost);

        // Page 5: Kapılar & Güvenlik
        tvDoorStatus = findViewById(R.id.tv_door_status);
        btnDoorToggle = findViewById(R.id.btn_door_toggle);
        tvTrunkStatus = findViewById(R.id.tv_trunk_status);
        btnTrunkToggle = findViewById(R.id.btn_trunk_toggle);
        tvPhoneKeyBadge = findViewById(R.id.tv_phone_key_badge);
        tvPhoneKeyInfo = findViewById(R.id.tv_phone_key_info);
        btnCopyKeyToWatch = findViewById(R.id.btn_copy_key_to_watch);
        btnManageDigitalKey = findViewById(R.id.btn_manage_digital_key);

        // Page 6: Sürüş İstatistikleri
        tvDriveMode = findViewById(R.id.tv_drive_mode);
        tvOdometer = findViewById(R.id.tv_odometer);
        tvTripDist = findViewById(R.id.tv_trip_dist);
        tvTripDur = findViewById(R.id.tv_trip_dur);
        tvTripConsumption = findViewById(R.id.tv_trip_consumption);
        tvTripSpeed = findViewById(R.id.tv_trip_speed);
        btnTripCurrent = findViewById(R.id.btn_trip_current);
        btnTripCharge = findViewById(R.id.btn_trip_charge);
        btnTripMy = findViewById(R.id.btn_trip_my);

        // Page 7: Konum & Park
        tvParkAddress = findViewById(R.id.tv_park_address);
        tvGpsCoords = findViewById(R.id.tv_gps_coords);
        tvParkDuration = findViewById(R.id.tv_park_duration);
        btnWalkToCar = findViewById(R.id.btn_walk_to_car);

        // Global Action Buttons
        btnRefresh = findViewById(R.id.btn_refresh);

        // Page 0: Ana Sayfa
        homeCardsContainer = findViewById(R.id.home_cards_container);
        btnCustomizeHome = findViewById(R.id.btn_customize_home);
        tvHomeSummary = findViewById(R.id.tv_home_summary);
    }

    private void setupWindowInsets() {
        View decor = getWindow().getDecorView();
        decor.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();

            if (statusBarSpacer != null) {
                // Ensure at least 60dp on S25 Ultra to clear status bar + camera punch hole comfortably
                int calculatedTop = Math.max(top + dpToPx(14), dpToPx(60));
                statusBarSpacer.getLayoutParams().height = calculatedTop;
                statusBarSpacer.requestLayout();
            }

            if (navBarSpacer != null) {
                int calculatedBottom = Math.max(bottom + dpToPx(16), dpToPx(32));
                navBarSpacer.getLayoutParams().height = calculatedBottom;
                navBarSpacer.requestLayout();
            }

            return view.onApplyWindowInsets(insets);
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
                getColor(R.color.accent_togg),
                getColor(R.color.accent_blue)
        );
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.card_bg));
        swipeRefresh.setOnRefreshListener(this::refreshTelemetry);
    }

    private void setupListeners() {
        // Tab Selection Click Listeners
        for (int i = 0; i < tabButtons.length; i++) {
            final int index = i;
            if (tabButtons[i] != null) {
                tabButtons[i].setOnClickListener(v -> selectTab(index));
            }
        }

        // Trumore Bridge Launches
        findViewById(R.id.btn_open_trumore).setOnClickListener(v -> {
            boolean opened = TrumoreBridge.openSmartDevice(this);
            if (!opened) {
                Toast.makeText(this, "Trumore uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_smart_access).setOnClickListener(v -> {
            boolean opened = TrumoreBridge.openSmartAccess(this);
            if (!opened) {
                Toast.makeText(this, "Trumore uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
            }
        });

        btnRefresh.setOnClickListener(v -> refreshTelemetry());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsDialog());

        if (tvVehicleVin != null) {
            tvVehicleVin.setOnClickListener(v -> showVinInputDialog(null));
        }
        if (tvVehiclePlate != null) {
            tvVehiclePlate.setOnClickListener(v -> showVinInputDialog(null));
        }

        tvSyncStatus.setOnClickListener(v -> {
            if (storage.isDemoMode() || !storage.hasValidToken()) {
                launchToggLogin();
            } else {
                showSettingsDialog();
            }
        });

        // Vehicle Controls: Door Lock Toggle
        btnDoorToggle.setOnClickListener(v -> toggleDoorLock());

        // Vehicle Controls: Trunk Open/Close with safety confirmation
        btnTrunkToggle.setOnClickListener(v -> confirmAndToggleTrunk());

        // Page 5: Dijital Anahtar Actions
        if (btnCopyKeyToWatch != null) {
            btnCopyKeyToWatch.setOnClickListener(v -> {
                DigitalKeyManager keyMgr = new DigitalKeyManager(this);
                DigitalKey key = keyMgr.getActiveKey();
                if (key != null && key.isValid()) {
                    WearSyncManager.syncDigitalKey(this, key);
                    Toast.makeText(this, "Dijital anahtar Samsung Galaxy Watch'a aktarılıyor... ⌚", Toast.LENGTH_SHORT).show();
                } else {
                    showManageDigitalKeyDialog();
                }
            });
        }

        if (btnManageDigitalKey != null) {
            btnManageDigitalKey.setOnClickListener(v -> showManageDigitalKeyDialog());
        }

        // Vehicle Controls: Climate Temp Minus / Plus
        btnTempMinus.setOnClickListener(v -> adjustTargetTemperature(-0.5));
        btnTempPlus.setOnClickListener(v -> adjustTargetTemperature(0.5));

        // Vehicle Controls: Climate Toggle
        btnClimateToggle.setOnClickListener(v -> toggleClimate());

        // Vehicle Controls: Defrost Toggle
        btnDefrost.setOnClickListener(v -> toggleDefrost());

        // Page 7: Walk to Car Map Navigation
        if (btnWalkToCar != null) {
            btnWalkToCar.setOnClickListener(v -> openNavigationToCar());
        }

        // Page 0: Ana Sayfayı Özelleştir
        if (btnCustomizeHome != null) {
            btnCustomizeHome.setOnClickListener(v -> showCustomizeHomeDialog());
        }

        // Page 6: 3-Way Trip Selector
        if (btnTripCurrent != null) {
            btnTripCurrent.setOnClickListener(v -> setTripType(0));
        }
        if (btnTripCharge != null) {
            btnTripCharge.setOnClickListener(v -> setTripType(1));
        }
        if (btnTripMy != null) {
            btnTripMy.setOnClickListener(v -> setTripType(2));
        }
    }

    public void selectTab(int index) {
        if (index < 0 || index >= tabButtons.length) return;
        selectedTabIndex = index;

        for (int i = 0; i < tabButtons.length; i++) {
            boolean isActive = (i == index);
            if (tabButtons[i] != null) {
                tabButtons[i].setBackgroundResource(isActive ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
                tabButtons[i].setTextColor(getColor(isActive ? R.color.accent_togg : R.color.text_secondary));
                tabButtons[i].setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (tabPages[i] != null) {
                tabPages[i].setVisibility(isActive ? View.VISIBLE : View.GONE);
            }
        }

        if (scrollRoot != null) {
            scrollRoot.smoothScrollTo(0, 0);
        }

        if (tabScrollView != null && tabButtons[index] != null) {
            tabScrollView.post(() -> {
                int scrollX = tabButtons[index].getLeft() - dpToPx(24);
                tabScrollView.smoothScrollTo(Math.max(0, scrollX), 0);
            });
        }
    }

    private void openNavigationToCar() {
        double lat = currentTelemetry != null ? currentTelemetry.latitude : 39.925054;
        double lon = currentTelemetry != null ? currentTelemetry.longitude : 32.834755;
        Uri gmmIntentUri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(Togg T10F Park Yeri)");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapIntent);
        } catch (Exception e) {
            Intent webMaps = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon));
            startActivity(webMaps);
        }
    }

    private void toggleDoorLock() {
        if (!storage.isDemoMode() && storage.hasValidToken()) {
            Toast.makeText(this, "Trumore Akıllı Erişim açılıyor...", Toast.LENGTH_SHORT).show();
            TrumoreBridge.openDoorControl(this);
            return;
        }

        isDoorLocked = !isDoorLocked;
        updateDoorUi();
        String message = isDoorLocked ? "Togg T10F: Kapılar Kilitlendi" : "Togg T10F: Kapı Kilitleri Açıldı";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        if (currentTelemetry != null) {
            currentTelemetry = currentTelemetry.withLock(isDoorLocked);
            dbHelper.insert(currentTelemetry);
            renderHomeCards();
        }
    }

    private void updateDoorUi() {
        if (tvDoorStatus == null) return;
        if (isDoorLocked) {
            tvDoorStatus.setText(R.string.door_locked);
            tvDoorStatus.setTextColor(getColor(R.color.status_green));
            if (btnDoorToggle != null) {
                btnDoorToggle.setText("Kapı Kilitlerini Aç");
            }
        } else {
            tvDoorStatus.setText(R.string.door_unlocked);
            tvDoorStatus.setTextColor(getColor(R.color.status_amber));
            if (btnDoorToggle != null) {
                btnDoorToggle.setText("Kapıları Kilitle");
            }
        }
    }

    private void confirmAndToggleTrunk() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.trunk_confirm_title);
        builder.setMessage(R.string.trunk_confirm_msg);
        builder.setPositiveButton("Evet, " + (isTrunkOpen ? "Kapat" : "Aç"), (dialog, which) -> {
            if (!storage.isDemoMode() && storage.hasValidToken()) {
                TrumoreBridge.openTrunkControl(this);
                return;
            }

            isTrunkOpen = !isTrunkOpen;
            updateTrunkUi();
            String message = isTrunkOpen ? "Bagaj Kapağı Açıldı" : "Bagaj Kapağı Kapatıldı";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withTrunk(isTrunkOpen);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.create().show();
    }

    private void updateTrunkUi() {
        if (tvTrunkStatus == null) return;
        if (isTrunkOpen) {
            tvTrunkStatus.setText(R.string.trunk_opened);
            tvTrunkStatus.setTextColor(getColor(R.color.status_amber));
            if (btnTrunkToggle != null) {
                btnTrunkToggle.setText("Bagajı Kapat");
            }
        } else {
            tvTrunkStatus.setText(R.string.trunk_closed);
            tvTrunkStatus.setTextColor(getColor(R.color.text_muted));
            if (btnTrunkToggle != null) {
                btnTrunkToggle.setText("Bagajı Aç");
            }
        }
    }

    private void adjustTargetTemperature(double delta) {
        currentTargetTemp = Math.max(16.0, Math.min(28.5, currentTargetTemp + delta));
        tvControlTargetTemp.setText(String.format("%.1f°C", currentTargetTemp));
        tvTargetHvac.setText(String.format("%.1f°C", currentTargetTemp));
        if (currentTelemetry != null) {
            currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, isClimateActive, isDefrostActive);
            renderHomeCards();
        }
    }

    private void toggleClimate() {
        if (!storage.isDemoMode() && storage.hasValidToken()) {
            Toast.makeText(this, "Trumore İklimlendirme açılıyor...", Toast.LENGTH_SHORT).show();
            TrumoreBridge.openClimateControl(this);
            return;
        }

        isClimateActive = !isClimateActive;
        updateClimateUi();

        if (isClimateActive) {
            Toast.makeText(this, "Klima Başlatıldı: " + String.format("%.1f°C", currentTargetTemp), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Klima Durduruldu", Toast.LENGTH_SHORT).show();
        }

        if (currentTelemetry != null) {
            currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, isClimateActive, isDefrostActive);
            dbHelper.insert(currentTelemetry);
            renderHomeCards();
        }
    }

    private void toggleDefrost() {
        isDefrostActive = !isDefrostActive;
        updateClimateUi();

        if (isDefrostActive) {
            Toast.makeText(this, "Ön Cam Buğu Çözme (Maksimum Isıtma) Aktif", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Ön Cam Buğu Çözme Kapatıldı", Toast.LENGTH_SHORT).show();
        }

        if (currentTelemetry != null) {
            currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, isClimateActive, isDefrostActive);
            dbHelper.insert(currentTelemetry);
            renderHomeCards();
        }
    }

    private void updateClimateUi() {
        if (tvClimateStatus != null) {
            if (isClimateActive) {
                tvClimateStatus.setText("Açık (" + String.format("%.1f°C", currentTargetTemp) + ")");
                tvClimateStatus.setTextColor(getColor(R.color.status_green));
                if (btnClimateToggle != null) {
                    btnClimateToggle.setText(R.string.action_stop_climate);
                }
            } else {
                tvClimateStatus.setText(R.string.climate_off);
                tvClimateStatus.setTextColor(getColor(R.color.text_muted));
                if (btnClimateToggle != null) {
                    btnClimateToggle.setText(R.string.action_start_climate);
                }
            }
        }
        if (btnDefrost != null) {
            btnDefrost.setTextColor(getColor(isDefrostActive ? R.color.status_amber : R.color.accent_blue));
        }
    }

    private void refreshTelemetry() {
        btnRefresh.setEnabled(false);
        swipeRefresh.setRefreshing(true);
        tvSyncStatus.setText("GÜNCELLENİYOR...");

        apiClient.fetchTelemetry(new TvaApiClient.TelemetryCallback() {
            @Override
            public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                currentTelemetry = telemetry;
                dbHelper.insert(telemetry);
                long syncTime = telemetry.timestampMillis > 0 ? telemetry.timestampMillis : System.currentTimeMillis();
                storage.setLastSync(syncTime);
                runOnUiThread(() -> {
                    renderTelemetry(telemetry, !isLive);
                    btnRefresh.setEnabled(true);
                    swipeRefresh.setRefreshing(false);
                    if (isLive) {
                        Toast.makeText(MainActivity.this, "Canlı telemetri güncellendi (" + formatSyncTime(syncTime) + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, VehicleTelemetry fallback) {
                currentTelemetry = fallback;
                runOnUiThread(() -> {
                    renderTelemetry(fallback, true);
                    btnRefresh.setEnabled(true);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();

                    // If VIN is missing, default demo VIN, or vehicle permission error
                    if (storage.isDemoVin() || (message != null && (message.contains("Şasi") || message.contains("şasi") || message.contains("VIN") || message.contains("permission") || message.contains("access")))) {
                        showVinInputDialog(message);
                    } else if (message != null && (message.contains("Oturum Süresi Doldu") || !storage.hasValidToken())) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Togg ID Girişi")
                                .setMessage("Togg ID oturumunuzun süresi doldu veya yetkilendirme yenilenemedi. Yeniden giriş yapmak ister misiniz?")
                                .setPositiveButton("Giriş Yap", (dialog, which) -> launchToggLogin())
                                .setNegativeButton("Daha Sonra", null)
                                .show();
                    }
                });
            }
        });
    }

    private void renderTelemetry(VehicleTelemetry t, boolean isDemo) {
        if (t == null) return;

        // Vehicle Header with VIN and Last Sync Timestamp
        tvVehicleTitle.setText(getString(R.string.vehicle_title));
        tvVehiclePlate.setText(t.plate != null && !t.plate.isEmpty() ? t.plate : getString(R.string.plate_placeholder));
        if (tvVehicleVin != null) {
            long syncTime = t.timestampMillis > 0 ? t.timestampMillis : storage.getLastSync();
            String timeStr = formatSyncTime(syncTime);
            String vinStr = (t.vin != null && !t.vin.isEmpty()) ? t.vin : "-";
            tvVehicleVin.setText("Şasi: " + vinStr + " • Son Güncelleme: " + timeStr);
        }
        tvSyncStatus.setText(isDemo ? getString(R.string.status_demo) : getString(R.string.status_live));

        // 1. Batarya & Şarj
        tvBatterySoc.setText(t.batterySocPercent + "%");
        tvRangeKm.setText(t.estimatedRangeKm + " km");
        tvChargingStatus.setText(t.isCharging
                ? getString(R.string.charging_active) + " (" + String.format("%.1f", t.chargePowerKw) + " kW)"
                : getString(R.string.not_charging));
        if (tvChargeMode != null) {
            tvChargeMode.setText(t.chargeMode != null ? t.chargeMode : "AC Ev Şarjı");
        }
        if (tvChargePower != null) {
            tvChargePower.setText(String.format("%.1f kW", t.chargePowerKw));
        }
        if (tvBatteryCapacity != null) {
            tvBatteryCapacity.setText(String.format("%.1f kWh", t.batteryTotalCapacityKwh));
        }
        if (tvLastChargeKwh != null) {
            tvLastChargeKwh.setText(String.format("+%.1f kWh", t.lastChargeEnergyKwh));
        }

        // 2. 12V Akü & Sistem
        tv12vSoc.setText("%" + t.lowVoltage12vPercent);
        if (t.is12vBatteryLow()) {
            tv12vStatus.setText(getString(R.string.aux_battery_warn));
            tv12vStatus.setTextColor(getColor(R.color.status_red));
            tv12vSoc.setTextColor(getColor(R.color.status_red));
        } else {
            tv12vStatus.setText(getString(R.string.aux_battery_ok));
            tv12vStatus.setTextColor(getColor(R.color.status_green));
            tv12vSoc.setTextColor(getColor(R.color.status_green));
        }
        if (tv12vVolts != null) {
            tv12vVolts.setText(String.format("Voltaj: %.1f V", t.lowVoltage12vVolts));
        }

        // 3. Lastikler & TPMS (PSI olarak gösterim)
        tvTpFL.setText(String.format(Locale.US, "%.1f psi • %d°C", t.getTirePressurePsiFL(), t.tireTempFL));
        tvTpFR.setText(String.format(Locale.US, "%.1f psi • %d°C", t.getTirePressurePsiFR(), t.tireTempFR));
        tvTpRL.setText(String.format(Locale.US, "%.1f psi • %d°C", t.getTirePressurePsiRL(), t.tireTempRL));
        tvTpRR.setText(String.format(Locale.US, "%.1f psi • %d°C", t.getTirePressurePsiRR(), t.tireTempRR));

        // 4. İklimlendirme / HVAC
        tvCabinTemp.setText(String.format("%.1f°C", t.cabinTempCelsius));
        currentTargetTemp = t.targetHvacCelsius > 10 ? t.targetHvacCelsius : 21.5;
        tvTargetHvac.setText(String.format("%.1f°C", currentTargetTemp));
        tvControlTargetTemp.setText(String.format("%.1f°C", currentTargetTemp));
        tvOutdoorTemp.setText(String.format("%.1f°C", t.outdoorTempCelsius));

        isClimateActive = t.isClimateActive;
        isDefrostActive = t.isDefrostActive;
        updateClimateUi();

        // 5. Kapılar & Güvenlik
        isDoorLocked = t.isLocked;
        isTrunkOpen = t.trunkOpen;
        updateDoorUi();
        updateTrunkUi();

        // 6. Sürüş İstatistikleri
        if (tvDriveMode != null) {
            tvDriveMode.setText(formatDriveMode(t.driveMode));
        }
        tvOdometer.setText(String.format("%,d km", t.odometerKm));
        updateTripStatsUi();

        // 7. Konum & Park
        resolveParkAddress(t.latitude, t.longitude);
        updateParkDuration(t.timestampMillis);

        // 0. Ana Sayfa Kartları (Custom Order)
        renderHomeCards();

        // 5. Dijital Anahtar Kartı (Page 5)
        renderDigitalKeyCard();

        // Sync to Samsung Galaxy Watch (Wear OS)
        com.togg.companion.wearable.WearSyncManager.syncTelemetry(this, t);
    }

    private void handleActionIntent(Intent intent) {
        if (intent == null) return;
        String cmd = intent.getStringExtra("action_command");
        if (cmd == null && intent.getAction() != null) {
            String act = intent.getAction();
            if (act.contains("LOCK") && !act.contains("UNLOCK")) cmd = "lock";
            else if (act.contains("UNLOCK")) cmd = "unlock";
            else if (act.contains("CLIMATE_START")) cmd = "climate_start";
            else if (act.contains("CLIMATE_STOP")) cmd = "climate_stop";
            else if (act.contains("DEFROST")) cmd = "defrost";
            else if (act.contains("TRUNK")) cmd = "trunk";
            else if (act.contains("LOCATE")) cmd = "locate";
        }
        if (cmd != null) {
            switch (cmd.toLowerCase(Locale.ROOT)) {
                case "lock":
                    if (!isDoorLocked) toggleDoorLock();
                    break;
                case "unlock":
                    if (isDoorLocked) toggleDoorLock();
                    break;
                case "climate_start":
                    if (!isClimateActive) toggleClimate();
                    break;
                case "climate_stop":
                    if (isClimateActive) toggleClimate();
                    break;
                case "defrost":
                    if (!isDefrostActive) toggleDefrost();
                    break;
                case "trunk":
                    confirmAndToggleTrunk();
                    break;
                case "locate":
                    openNavigationToCar();
                    break;
            }
        }
    }

    public void executeDigitalKeyAction(String action) {
        DigitalKeyManager keyMgr = new DigitalKeyManager(this);
        DigitalKey activeKey = keyMgr.getActiveKey();
        if (activeKey == null || !activeKey.isValid()) {
            Toast.makeText(this, "Geçerli bir Dijital Anahtar bulunamadı!", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("lock".equalsIgnoreCase(action)) {
            if (!activeKey.canLock) {
                Toast.makeText(this, "Bu anahtarın kapı kilitleme yetkisi yok!", Toast.LENGTH_SHORT).show();
                return;
            }
            isDoorLocked = true;
            updateDoorUi();
            Toast.makeText(this, "🔑 Dijital Anahtar: Kapılar kilitlendi (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withLock(true);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        } else if ("unlock".equalsIgnoreCase(action)) {
            if (!activeKey.canUnlock) {
                Toast.makeText(this, "Bu anahtarın kilit açma yetkisi yok!", Toast.LENGTH_SHORT).show();
                return;
            }
            isDoorLocked = false;
            updateDoorUi();
            Toast.makeText(this, "🔑 Dijital Anahtar: Kapı kilitleri açıldı (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withLock(false);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        }
    }

    private void handleDigitalKeyIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        DigitalKey parsedKey = null;

        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            parsedKey = DigitalKeyManager.parseFromUri(intent.getData());
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                parsedKey = DigitalKeyManager.parseFromInput(sharedText);
            }
        }

        if (parsedKey != null) {
            showRegisterDigitalKeyDialog(parsedKey);
        }
    }

    private void showRegisterDigitalKeyDialog(DigitalKey key) {
        if (key == null || isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔑 Trumore Dijital Anahtar Paylaşımı");

        String msg = "Trumore uygulamasından yeni bir dijital anahtar paylaşımı algılandı:\n\n"
                + "• Plaka / Araç: " + key.vehiclePlate + " (" + key.vehicleVin + ")\n"
                + "• Güvenli Kod (SE-ID): " + key.seId + "\n"
                + "• Kullanıcı: " + key.recipientName + "\n"
                + "• Geçerlilik: " + key.getRemainingTimeDisplay() + "\n"
                + "• Yetkiler: " + key.getPermissionsSummary() + "\n\n"
                + "Bu anahtarı ToggCompanion'a kaydedip Samsung Galaxy Watch akıllı saatinize kopyalamak istiyor musunuz?";
        builder.setMessage(msg);

        builder.setPositiveButton("Kaydet & Saate Aktar ⌚", (dialog, which) -> {
            DigitalKeyManager mgr = new DigitalKeyManager(this);
            mgr.saveKey(key);
            WearSyncManager.syncDigitalKey(this, key);
            renderDigitalKeyCard();
            renderHomeCards();
            selectTab(5); // Switch to Security tab
            Toast.makeText(this, "Dijital anahtar başarıyla kaydedildi ve akıllı saate aktarıldı! ⌚🔑", Toast.LENGTH_LONG).show();
        });

        builder.setNeutralButton("Sadece Telefona Kaydet", (dialog, which) -> {
            DigitalKeyManager mgr = new DigitalKeyManager(this);
            mgr.saveKey(key);
            renderDigitalKeyCard();
            renderHomeCards();
            selectTab(5);
            Toast.makeText(this, "Dijital anahtar telefona kaydedildi.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    private void showManageDigitalKeyDialog() {
        if (isFinishing() || isDestroyed()) return;

        DigitalKeyManager keyMgr = new DigitalKeyManager(this);
        DigitalKey currentKey = keyMgr.getActiveKey();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔑 Trumore Dijital Anahtar Yönetimi");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));

        if (currentKey != null) {
            TextView tvCur = new TextView(this);
            tvCur.setText("Mevcut Kayıtlı Anahtar:\n• SE-ID: " + currentKey.seId + "\n• Plaka: " + currentKey.vehiclePlate + "\n• Süre: " + currentKey.getRemainingTimeDisplay() + "\n• Durum: " + (currentKey.isValid() ? "Aktif ✅" : "Süresi Dolmuş ⚠️"));
            tvCur.setTextColor(getColor(R.color.text_primary));
            tvCur.setTextSize(13);
            tvCur.setPadding(0, 0, 0, dpToPx(10));
            layout.addView(tvCur);
        }

        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("Trumore'dan kopyalanan davet linki, SMS metni veya referans kodu (Örn: SE-8F4219):");
        tvPrompt.setTextColor(getColor(R.color.text_secondary));
        tvPrompt.setTextSize(12);
        layout.addView(tvPrompt);

        final EditText etKeyInput = new EditText(this);
        etKeyInput.setHint("https://trumore.app/keysharingInvitation?seId=... veya SE-XXXXXX");
        etKeyInput.setTextSize(14);
        layout.addView(etKeyInput);

        builder.setView(layout);

        builder.setPositiveButton("Kaydet ve Saate Aktar", (dialog, which) -> {
            String input = etKeyInput.getText().toString().trim();
            if (input.isEmpty()) {
                if (currentKey != null && currentKey.isValid()) {
                    WearSyncManager.syncDigitalKey(this, currentKey);
                    Toast.makeText(this, "Mevcut anahtar akıllı saate aktarıldı! ⌚", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Lütfen bir anahtar bağlantısı veya kodu giriniz.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            DigitalKey parsed = DigitalKeyManager.parseFromInput(input);
            if (parsed != null) {
                keyMgr.saveKey(parsed);
                WearSyncManager.syncDigitalKey(this, parsed);
                renderDigitalKeyCard();
                renderHomeCards();
                Toast.makeText(this, "Dijital anahtar kaydedildi ve saate kopyalandı! ⌚🔑", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Geçersiz anahtar formatı! Trumore bağlantısı veya SE- referans kodu bekleniyor.", Toast.LENGTH_LONG).show();
            }
        });

        if (currentKey != null) {
            builder.setNeutralButton("🗑️ Sil", (dialog, which) -> {
                keyMgr.removeKey();
                WearSyncManager.syncDigitalKey(this, null);
                renderDigitalKeyCard();
                renderHomeCards();
                Toast.makeText(this, "Dijital anahtar silindi ve saatten kaldırıldı.", Toast.LENGTH_SHORT).show();
            });
        }

        builder.setNegativeButton("Kapat", null);
        builder.show();
    }

    private void renderDigitalKeyCard() {
        DigitalKeyManager keyMgr = new DigitalKeyManager(this);
        DigitalKey key = keyMgr.getActiveKey();

        if (tvPhoneKeyBadge != null) {
            if (key != null && key.isValid()) {
                tvPhoneKeyBadge.setText("AKTİF");
                tvPhoneKeyBadge.setTextColor(getColor(R.color.status_green));
            } else if (key != null && !key.isValid()) {
                tvPhoneKeyBadge.setText("SÜRESİ DOLDU");
                tvPhoneKeyBadge.setTextColor(getColor(R.color.status_red));
            } else {
                tvPhoneKeyBadge.setText("YOK");
                tvPhoneKeyBadge.setTextColor(getColor(R.color.text_muted));
            }
        }

        if (tvPhoneKeyInfo != null) {
            if (key != null && key.isValid()) {
                String info = "Araç: " + key.vehiclePlate + " (" + key.vehicleVin + ")\n"
                        + "SE-ID: " + key.seId + "\n"
                        + "Kullanıcı: " + key.recipientName + "\n"
                        + "Kalan Süre: " + key.getRemainingTimeDisplay() + "\n"
                        + "Yetkiler: " + key.getPermissionsSummary();
                tvPhoneKeyInfo.setText(info);
            } else if (key != null && !key.isValid()) {
                tvPhoneKeyInfo.setText("Kayıtlı dijital anahtarın (" + key.seId + ") geçerlilik süresi dolmuştur.");
            } else {
                tvPhoneKeyInfo.setText("Kayıtlı dijital anahtar bulunamadı. Trumore uygulamasından paylaşılan davet bağlantısını açarak veya referans kodu girerek anahtar ekleyebilirsiniz.");
            }
        }
    }

    // ==========================================
    // SÜRÜŞ MODU, YOLCULUK İSTATİSTİKLERİ & PARK
    // ==========================================

    private void setTripType(int type) {
        selectedTripType = type;
        if (btnTripCurrent != null) {
            boolean active = (type == 0);
            btnTripCurrent.setBackgroundResource(active ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
            btnTripCurrent.setTextColor(getColor(active ? R.color.accent_togg : R.color.text_secondary));
            btnTripCurrent.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (btnTripCharge != null) {
            boolean active = (type == 1);
            btnTripCharge.setBackgroundResource(active ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
            btnTripCharge.setTextColor(getColor(active ? R.color.accent_togg : R.color.text_secondary));
            btnTripCharge.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (btnTripMy != null) {
            boolean active = (type == 2);
            btnTripMy.setBackgroundResource(active ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
            btnTripMy.setTextColor(getColor(active ? R.color.accent_togg : R.color.text_secondary));
            btnTripMy.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        updateTripStatsUi();
    }

    private void updateTripStatsUi() {
        if (currentTelemetry == null) return;
        VehicleTelemetry.TripData trip;
        if (selectedTripType == 0) {
            trip = currentTelemetry.currentTrip != null ? currentTelemetry.currentTrip :
                    new VehicleTelemetry.TripData(currentTelemetry.lastTripDistanceKm, currentTelemetry.lastTripDurationMin, currentTelemetry.lastTripAvgConsumption, currentTelemetry.lastTripAvgSpeed);
        } else if (selectedTripType == 1) {
            trip = currentTelemetry.lastChargeTrip != null ? currentTelemetry.lastChargeTrip :
                    new VehicleTelemetry.TripData(85.0, 110, 17.2, 54);
        } else {
            trip = currentTelemetry.myTrip != null ? currentTelemetry.myTrip :
                    new VehicleTelemetry.TripData(currentTelemetry.odometerKm, 14500, 17.8, 56);
        }

        if (tvTripDist != null) {
            tvTripDist.setText(String.format(Locale.US, "%.1f km", trip.distanceKm));
        }
        if (tvTripDur != null) {
            int h = trip.durationMinutes / 60;
            int m = trip.durationMinutes % 60;
            if (h > 0) {
                tvTripDur.setText(String.format(Locale.getDefault(), "%d sa %d dk", h, m));
            } else {
                tvTripDur.setText(String.format(Locale.getDefault(), "%d dk", m));
            }
        }
        if (tvTripConsumption != null) {
            tvTripConsumption.setText(String.format(Locale.US, "%.1f kWh", trip.avgConsumption));
        }
        if (tvTripSpeed != null) {
            tvTripSpeed.setText(String.format(Locale.getDefault(), "%d km/s", trip.avgSpeed));
        }
    }

    private String formatDriveMode(String rawMode) {
        if (rawMode == null || rawMode.trim().isEmpty()) {
            return "Sürüş Modu: Konfor";
        }
        String m = rawMode.toLowerCase(Locale.ROOT);
        if (m.contains("eco")) {
            return "Sürüş Modu: Eco";
        } else if (m.contains("sport") || m.contains("spor")) {
            return "Sürüş Modu: Spor";
        } else if (m.contains("comfort") || m.contains("konfor")) {
            return "Sürüş Modu: Konfor";
        } else if (m.contains("individual") || m.contains("bireysel")) {
            return "Sürüş Modu: Bireysel";
        }
        String cleaned = rawMode.replace("drivemode_", "").replace("drive_mode_", "").replace("_", " ").trim();
        return "Sürüş Modu: " + cleaned;
    }

    private void resolveParkAddress(double lat, double lon) {
        if (tvGpsCoords != null) {
            tvGpsCoords.setText(String.format(Locale.US, "GPS: %.6f, %.6f", lat, lon));
        }
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, new Locale("tr", "TR"));
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    StringBuilder sb = new StringBuilder();
                    if (a.getSubLocality() != null) {
                        sb.append(a.getSubLocality()).append(", ");
                    } else if (a.getThoroughfare() != null) {
                        sb.append(a.getThoroughfare()).append(", ");
                    }
                    if (a.getSubAdminArea() != null) {
                        sb.append(a.getSubAdminArea()).append(" / ");
                    } else if (a.getLocality() != null) {
                        sb.append(a.getLocality()).append(" / ");
                    }
                    if (a.getAdminArea() != null) {
                        sb.append(a.getAdminArea());
                    }
                    String res = sb.toString().trim();
                    if (res.endsWith(",") || res.endsWith("/")) {
                        res = res.substring(0, res.length() - 1).trim();
                    }
                    if (!res.isEmpty()) {
                        String finalRes = res;
                        runOnUiThread(() -> {
                            if (tvParkAddress != null) tvParkAddress.setText(finalRes);
                        });
                        return;
                    }
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                if (tvParkAddress != null) {
                    tvParkAddress.setText(String.format(Locale.US, "Konum: %.4f, %.4f", lat, lon));
                }
            });
        }).start();
    }

    private void updateParkDuration(long timestampMillis) {
        if (tvParkDuration == null) return;
        long elapsed = System.currentTimeMillis() - timestampMillis;
        if (elapsed < 0) elapsed = 0;
        long minutes = elapsed / (1000 * 60);
        if (minutes < 1) {
            tvParkDuration.setText("Park Süresi: Canlı (Az önce güncellendi)");
        } else if (minutes < 60) {
            tvParkDuration.setText(String.format(Locale.getDefault(), "Park Süresi: %d dakika önce güncellendi", minutes));
        } else {
            long h = minutes / 60;
            long m = minutes % 60;
            tvParkDuration.setText(String.format(Locale.getDefault(), "Park Süresi: %d saat %d dakika", h, m));
        }
    }

    // ==========================================
    // ANA SAYFA (DASHBOARD) KARTLARI & DÜZENLEME
    // ==========================================

    private static final String CARD_BATTERY = "battery";
    private static final String CARD_CONTROLS = "controls";
    private static final String CARD_DRIVE = "drive";
    private static final String CARD_TIRES = "tires";
    private static final String CARD_LOCATION = "location";
    private static final String CARD_CLIMATE = "climate";
    private static final String CARD_12V = "12v";
    private static final String CARD_DIGITAL_KEY = "digital_key";

    private void renderHomeCards() {
        if (homeCardsContainer == null) return;
        homeCardsContainer.removeAllViews();
        VehicleTelemetry t = currentTelemetry;
        if (t == null) return;

        String orderStr = storage.getHomeCardsOrder();
        String visibleStr = storage.getHomeCardsVisible();
        List<String> order = new ArrayList<>(Arrays.asList(orderStr.split(",")));
        Set<String> visibleSet = new HashSet<>(Arrays.asList(visibleStr.split(",")));

        for (String cardKey : order) {
            String key = cardKey.trim();
            if (!visibleSet.contains(key)) continue;

            View cardView = buildHomeCard(key, t);
            if (cardView != null) {
                homeCardsContainer.addView(cardView);
            }
        }
    }

    private View buildHomeCard(String key, VehicleTelemetry t) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dpToPx(14);
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        switch (key) {
            case CARD_BATTERY: {
                TextView title = createCardTitle("⚡ Yüksek Voltaj Batarya");
                card.addView(title);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = dpToPx(6);
                row.setLayoutParams(rowParams);

                TextView socTv = new TextView(this);
                socTv.setText("%" + t.batterySocPercent);
                socTv.setTextColor(getColor(R.color.text_primary));
                socTv.setTextSize(28);
                socTv.setTypeface(null, Typeface.BOLD);
                row.addView(socTv);

                TextView rangeTv = new TextView(this);
                rangeTv.setText("  •  " + t.estimatedRangeKm + " km menzil");
                rangeTv.setTextColor(getColor(R.color.accent_togg));
                rangeTv.setTextSize(16);
                rangeTv.setTypeface(null, Typeface.BOLD);
                row.addView(rangeTv);
                card.addView(row);

                TextView statusTv = new TextView(this);
                statusTv.setText(t.isCharging ? "⚡ Şarj Ediliyor (" + String.format(Locale.US, "%.1f", t.chargePowerKw) + " kW)" : "Şarjda Değil (" + t.chargeMode + ")");
                statusTv.setTextColor(getColor(t.isCharging ? R.color.status_green : R.color.text_muted));
                statusTv.setTextSize(12);
                statusTv.setPadding(0, dpToPx(6), 0, 0);
                card.addView(statusTv);

                TextView hintTv = new TextView(this);
                hintTv.setText("Detaylar için dokunun ➔");
                hintTv.setTextColor(getColor(R.color.text_muted));
                hintTv.setTextSize(10);
                hintTv.setPadding(0, dpToPx(6), 0, 0);
                card.addView(hintTv);

                card.setOnClickListener(v -> selectTab(1));
                return card;
            }
            case CARD_CONTROLS: {
                TextView title = createCardTitle("🎮 Hızlı Araç Kontrolleri");
                card.addView(title);

                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = dpToPx(10);
                btnRow.setLayoutParams(rowParams);

                Button lockBtn = new Button(this);
                lockBtn.setText(isDoorLocked ? "🔓 Kilit Aç" : "🔒 Kilitle");
                lockBtn.setTextColor(getColor(R.color.accent_togg));
                lockBtn.setBackgroundResource(R.drawable.btn_outline);
                lockBtn.setTextSize(11);
                LinearLayout.LayoutParams b1Params = new LinearLayout.LayoutParams(0, dpToPx(40), 1.0f);
                b1Params.rightMargin = dpToPx(4);
                lockBtn.setLayoutParams(b1Params);
                lockBtn.setOnClickListener(v -> toggleDoorLock());
                btnRow.addView(lockBtn);

                Button trunkBtn = new Button(this);
                trunkBtn.setText(isTrunkOpen ? "Kapat" : "🚗 Bagaj");
                trunkBtn.setTextColor(getColor(R.color.accent_togg));
                trunkBtn.setBackgroundResource(R.drawable.btn_outline);
                trunkBtn.setTextSize(11);
                LinearLayout.LayoutParams b2Params = new LinearLayout.LayoutParams(0, dpToPx(40), 1.0f);
                b2Params.leftMargin = dpToPx(2);
                b2Params.rightMargin = dpToPx(2);
                trunkBtn.setLayoutParams(b2Params);
                trunkBtn.setOnClickListener(v -> confirmAndToggleTrunk());
                btnRow.addView(trunkBtn);

                Button climBtn = new Button(this);
                climBtn.setText(isClimateActive ? "❄️ Kapat" : "❄️ Klima");
                climBtn.setTextColor(getColor(isClimateActive ? R.color.status_green : R.color.accent_togg));
                climBtn.setBackgroundResource(R.drawable.btn_outline);
                climBtn.setTextSize(11);
                LinearLayout.LayoutParams b3Params = new LinearLayout.LayoutParams(0, dpToPx(40), 1.0f);
                b3Params.leftMargin = dpToPx(4);
                climBtn.setLayoutParams(b3Params);
                climBtn.setOnClickListener(v -> toggleClimate());
                btnRow.addView(climBtn);

                card.addView(btnRow);
                return card;
            }
            case CARD_DRIVE: {
                TextView title = createCardTitle("📊 Sürüş ve Yolculuk");
                card.addView(title);

                TextView modeTv = new TextView(this);
                modeTv.setText(formatDriveMode(t.driveMode) + "  •  Toplam: " + String.format(Locale.getDefault(), "%,d km", t.odometerKm));
                modeTv.setTextColor(getColor(R.color.accent_togg));
                modeTv.setTextSize(12);
                modeTv.setTypeface(null, Typeface.BOLD);
                modeTv.setPadding(0, dpToPx(4), 0, dpToPx(4));
                card.addView(modeTv);

                VehicleTelemetry.TripData trip = t.currentTrip != null ? t.currentTrip :
                        new VehicleTelemetry.TripData(t.lastTripDistanceKm, t.lastTripDurationMin, t.lastTripAvgConsumption, t.lastTripAvgSpeed);
                int h = trip.durationMinutes / 60;
                int m = trip.durationMinutes % 60;
                String durStr = h > 0 ? (h + " sa " + m + " dk") : (m + " dk");

                TextView statsTv = new TextView(this);
                statsTv.setText(String.format(Locale.US, "Son Sürüş: %.1f km  •  %s  •  %.1f kWh/100km", trip.distanceKm, durStr, trip.avgConsumption));
                statsTv.setTextColor(getColor(R.color.text_primary));
                statsTv.setTextSize(13);
                card.addView(statsTv);

                TextView hintTv = new TextView(this);
                hintTv.setText("Tüm yolculuklar için dokunun ➔");
                hintTv.setTextColor(getColor(R.color.text_muted));
                hintTv.setTextSize(10);
                hintTv.setPadding(0, dpToPx(6), 0, 0);
                card.addView(hintTv);

                card.setOnClickListener(v -> selectTab(6));
                return card;
            }
            case CARD_TIRES: {
                TextView title = createCardTitle("🛞 Lastik Basınçları (PSI)");
                card.addView(title);

                TextView fTires = new TextView(this);
                fTires.setText(String.format(Locale.US, "Ön: Sol %.1f psi (%d°C)  |  Sağ %.1f psi (%d°C)",
                        t.getTirePressurePsiFL(), t.tireTempFL, t.getTirePressurePsiFR(), t.tireTempFR));
                fTires.setTextColor(getColor(R.color.text_primary));
                fTires.setTextSize(12);
                fTires.setPadding(0, dpToPx(4), 0, dpToPx(2));
                card.addView(fTires);

                TextView rTires = new TextView(this);
                rTires.setText(String.format(Locale.US, "Arka: Sol %.1f psi (%d°C)  |  Sağ %.1f psi (%d°C)",
                        t.getTirePressurePsiRL(), t.tireTempRL, t.getTirePressurePsiRR(), t.tireTempRR));
                rTires.setTextColor(getColor(R.color.text_primary));
                rTires.setTextSize(12);
                rTires.setPadding(0, 0, 0, dpToPx(4));
                card.addView(rTires);

                TextView statusTv = new TextView(this);
                boolean low = t.isAnyTireLow();
                statusTv.setText(low ? "⚠️ Düşük Basınç Uyarısı" : "✓ Fabrika Tavsiyesi: 36 PSI (Normal)");
                statusTv.setTextColor(getColor(low ? R.color.status_red : R.color.status_green));
                statusTv.setTextSize(11);
                card.addView(statusTv);

                card.setOnClickListener(v -> selectTab(3));
                return card;
            }
            case CARD_LOCATION: {
                TextView title = createCardTitle("📍 Son Park Yeri");
                card.addView(title);

                TextView addrTv = new TextView(this);
                addrTv.setText(tvParkAddress != null ? tvParkAddress.getText() : t.parkAddress);
                addrTv.setTextColor(getColor(R.color.text_primary));
                addrTv.setTextSize(14);
                addrTv.setTypeface(null, Typeface.BOLD);
                addrTv.setPadding(0, dpToPx(4), 0, dpToPx(2));
                card.addView(addrTv);

                TextView durTv = new TextView(this);
                durTv.setText(tvParkDuration != null ? tvParkDuration.getText() : "Park süresi hesaplanıyor...");
                durTv.setTextColor(getColor(R.color.status_green));
                durTv.setTextSize(12);
                durTv.setPadding(0, 0, 0, dpToPx(8));
                card.addView(durTv);

                Button navBtn = new Button(this);
                navBtn.setText("🧭 Haritada Aracıma Yürü");
                navBtn.setTextColor(getColor(R.color.white));
                navBtn.setBackgroundResource(R.drawable.btn_gradient);
                navBtn.setTextSize(12);
                navBtn.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40)));
                navBtn.setOnClickListener(v -> openNavigationToCar());
                card.addView(navBtn);

                return card;
            }
            case CARD_CLIMATE: {
                TextView title = createCardTitle("❄️ İklimlendirme & Sıcaklık");
                card.addView(title);

                TextView tempsTv = new TextView(this);
                tempsTv.setText(String.format(Locale.US, "Kabin: %.1f°C   •   Dış Ortam: %.1f°C   •   Hedef: %.1f°C",
                        t.cabinTempCelsius, t.outdoorTempCelsius, currentTargetTemp));
                tempsTv.setTextColor(getColor(R.color.text_primary));
                tempsTv.setTextSize(13);
                tempsTv.setPadding(0, dpToPx(4), 0, dpToPx(4));
                card.addView(tempsTv);

                TextView statusTv = new TextView(this);
                statusTv.setText(isClimateActive ? "✓ Klima Açık ve Çalışıyor" : "Klima Kapalı");
                statusTv.setTextColor(getColor(isClimateActive ? R.color.status_green : R.color.text_muted));
                statusTv.setTextSize(11);
                card.addView(statusTv);

                card.setOnClickListener(v -> selectTab(4));
                return card;
            }
            case CARD_12V: {
                TextView title = createCardTitle("🔋 12V Alçak Gerilim Aküsü");
                card.addView(title);

                TextView vTv = new TextView(this);
                vTv.setText(String.format(Locale.US, "Seviye: %%%d   •   Voltaj: %.1f V",
                        t.lowVoltage12vPercent, t.lowVoltage12vVolts));
                vTv.setTextColor(getColor(R.color.text_primary));
                vTv.setTextSize(13);
                vTv.setPadding(0, dpToPx(4), 0, dpToPx(4));
                card.addView(vTv);

                TextView statusTv = new TextView(this);
                statusTv.setText(t.is12vBatteryLow() ? "⚠️ Akü Seviyesi Düşük! Aracı çalıştırın." : "✓ Akü Durumu Sağlıklı");
                statusTv.setTextColor(getColor(t.is12vBatteryLow() ? R.color.status_red : R.color.status_green));
                statusTv.setTextSize(11);
                card.addView(statusTv);

                card.setOnClickListener(v -> selectTab(2));
                return card;
            }
            case CARD_DIGITAL_KEY: {
                TextView title = createCardTitle("🔑 Trumore Dijital Anahtar");
                title.setTextColor(getColor(R.color.accent_purple));
                card.addView(title);

                DigitalKeyManager keyMgr = new DigitalKeyManager(this);
                DigitalKey dKey = keyMgr.getActiveKey();

                TextView tvStatus = new TextView(this);
                tvStatus.setTextSize(13);
                tvStatus.setPadding(0, dpToPx(4), 0, dpToPx(8));
                if (dKey != null && dKey.isValid()) {
                    tvStatus.setText("Durum: ✅ Aktif (" + dKey.vehiclePlate + " • " + dKey.getRemainingTimeDisplay() + ")");
                    tvStatus.setTextColor(getColor(R.color.status_green));
                } else if (dKey != null && !dKey.isValid()) {
                    tvStatus.setText("Durum: ⚠️ Süresi Doldu");
                    tvStatus.setTextColor(getColor(R.color.status_red));
                } else {
                    tvStatus.setText("Durum: ⚠️ Henüz Anahtar Eklenmedi");
                    tvStatus.setTextColor(getColor(R.color.text_secondary));
                }
                card.addView(tvStatus);

                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);

                Button btnCopy = new Button(this);
                btnCopy.setText("⌚ Saate Kopyala");
                btnCopy.setTextSize(12);
                btnCopy.setTextColor(getColor(R.color.white));
                btnCopy.setBackgroundResource(R.drawable.btn_gradient);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
                p1.rightMargin = dpToPx(4);
                btnCopy.setLayoutParams(p1);
                btnCopy.setOnClickListener(v -> {
                    DigitalKey k = new DigitalKeyManager(this).getActiveKey();
                    if (k != null && k.isValid()) {
                        WearSyncManager.syncDigitalKey(this, k);
                        Toast.makeText(this, "Dijital anahtar akıllı saate aktarılıyor... ⌚", Toast.LENGTH_SHORT).show();
                    } else {
                        showManageDigitalKeyDialog();
                    }
                });
                btnRow.addView(btnCopy);

                Button btnManage = new Button(this);
                btnManage.setText("Yönet / Ekle");
                btnManage.setTextSize(12);
                btnManage.setTextColor(getColor(R.color.accent_purple));
                btnManage.setBackgroundResource(R.drawable.btn_outline);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
                p2.leftMargin = dpToPx(4);
                btnManage.setLayoutParams(p2);
                btnManage.setOnClickListener(v -> showManageDigitalKeyDialog());
                btnRow.addView(btnManage);

                card.addView(btnRow);
                card.setOnClickListener(v -> selectTab(5));
                return card;
            }
        }
        return null;
    }

    private TextView createCardTitle(String titleText) {
        TextView tv = new TextView(this);
        tv.setText(titleText);
        tv.setTextColor(getColor(R.color.accent_togg));
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    static class CardItem {
        final String key;
        final String title;
        boolean visible;

        CardItem(String key, String title, boolean visible) {
            this.key = key;
            this.title = title;
            this.visible = visible;
        }
    }

    private void showCustomizeHomeDialog() {
        if (isFinishing() || isDestroyed()) return;

        String orderStr = storage.getHomeCardsOrder();
        String visibleStr = storage.getHomeCardsVisible();
        List<String> orderKeys = new ArrayList<>(Arrays.asList(orderStr.split(",")));
        Set<String> visibleSet = new HashSet<>(Arrays.asList(visibleStr.split(",")));

        List<CardItem> items = new ArrayList<>();
        for (String k : orderKeys) {
            String key = k.trim();
            String name = getCardDisplayName(key);
            if (name != null) {
                items.add(new CardItem(key, name, visibleSet.contains(key)));
            }
        }
        String[] allKeys = {CARD_BATTERY, CARD_CONTROLS, CARD_DRIVE, CARD_TIRES, CARD_LOCATION, CARD_CLIMATE, CARD_12V, CARD_DIGITAL_KEY};
        for (String ak : allKeys) {
            boolean exists = false;
            for (CardItem it : items) {
                if (it.key.equals(ak)) { exists = true; break; }
            }
            if (!exists) {
                items.add(new CardItem(ak, getCardDisplayName(ak), true));
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Ana Sayfayı Özelleştir");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(12));
        scrollView.addView(listLayout);

        Runnable refreshList = new Runnable() {
            @Override
            public void run() {
                listLayout.removeAllViews();

                TextView desc = new TextView(MainActivity.this);
                desc.setText("Ana sayfada görünmesini istediğiniz kartları seçin ve ▲ ▼ butonlarıyla sırasını belirleyin:");
                desc.setTextColor(getColor(R.color.text_secondary));
                desc.setTextSize(12);
                desc.setPadding(0, 0, 0, dpToPx(10));
                listLayout.addView(desc);

                for (int i = 0; i < items.size(); i++) {
                    final int index = i;
                    final CardItem item = items.get(i);

                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dpToPx(4), 0, dpToPx(4));

                    CheckBox cb = new CheckBox(MainActivity.this);
                    cb.setChecked(item.visible);
                    cb.setOnCheckedChangeListener((btn, isChecked) -> item.visible = isChecked);
                    row.addView(cb);

                    TextView label = new TextView(MainActivity.this);
                    label.setText(item.title);
                    label.setTextColor(getColor(R.color.text_primary));
                    label.setTextSize(13);
                    LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    label.setLayoutParams(lParams);
                    row.addView(label);

                    Button btnUp = new Button(MainActivity.this);
                    btnUp.setText("▲");
                    btnUp.setTextSize(12);
                    btnUp.setEnabled(index > 0);
                    btnUp.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(42), dpToPx(38)));
                    btnUp.setOnClickListener(v -> {
                        if (index > 0) {
                            CardItem prev = items.remove(index - 1);
                            items.add(index, prev);
                            run();
                        }
                    });
                    row.addView(btnUp);

                    Button btnDown = new Button(MainActivity.this);
                    btnDown.setText("▼");
                    btnDown.setTextSize(12);
                    btnDown.setEnabled(index < items.size() - 1);
                    btnDown.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(42), dpToPx(38)));
                    btnDown.setOnClickListener(v -> {
                        if (index < items.size() - 1) {
                            CardItem next = items.remove(index + 1);
                            items.add(index, next);
                            run();
                        }
                    });
                    row.addView(btnDown);

                    listLayout.addView(row);
                }
            }
        };

        refreshList.run();
        builder.setView(scrollView);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            StringBuilder orderSb = new StringBuilder();
            StringBuilder visSb = new StringBuilder();
            for (CardItem it : items) {
                if (orderSb.length() > 0) orderSb.append(",");
                orderSb.append(it.key);
                if (it.visible) {
                    if (visSb.length() > 0) visSb.append(",");
                    visSb.append(it.key);
                }
            }
            storage.setHomeCardsOrder(orderSb.toString());
            storage.setHomeCardsVisible(visSb.toString());
            renderHomeCards();
            Toast.makeText(MainActivity.this, "Ana sayfa yerleşimi kaydedildi.", Toast.LENGTH_SHORT).show();
        });

        builder.setNeutralButton("Varsayılan", (dialog, which) -> {
            storage.setHomeCardsOrder(SecureTokenStorage.DEFAULT_HOME_CARDS);
            storage.setHomeCardsVisible(SecureTokenStorage.DEFAULT_HOME_CARDS);
            renderHomeCards();
            Toast.makeText(MainActivity.this, "Varsayılan yerleşime sıfırlandı.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    private String getCardDisplayName(String key) {
        switch (key) {
            case CARD_BATTERY: return "⚡ Yüksek Voltaj Batarya";
            case CARD_CONTROLS: return "🎮 Hızlı Kontroller";
            case CARD_DRIVE: return "📊 Sürüş & Yolculuk";
            case CARD_TIRES: return "🛞 Lastik Basınçları (PSI)";
            case CARD_LOCATION: return "📍 Son Park Yeri & Konum";
            case CARD_CLIMATE: return "❄️ İklimlendirme (HVAC)";
            case CARD_12V: return "🔋 12V Alçak Gerilim Aküsü";
            case CARD_DIGITAL_KEY: return "🔑 Trumore Dijital Anahtar";
            default: return null;
        }
    }

    private static final int REQ_TOGG_LOGIN = 101;

    private void launchToggLogin() {
        Intent intent = new Intent(this, ToggLoginActivity.class);
        startActivityForResult(intent, REQ_TOGG_LOGIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TOGG_LOGIN && resultCode == RESULT_OK) {
            if (storage.isDemoVin()) {
                showVinInputDialog("Togg ID Girişi Başarılı! 🎉");
            } else {
                Toast.makeText(this, "Togg ID ile canlı veriler senkronize ediliyor...", Toast.LENGTH_SHORT).show();
                refreshTelemetry();
            }
        }
    }

    private void showVinInputDialog(String headerMessage) {
        if (isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚗 Aracınızın Şasi Numarası (VIN)");

        StringBuilder msg = new StringBuilder();
        if (headerMessage != null && !headerMessage.isEmpty()) {
            msg.append(headerMessage).append("\n\n");
        }
        msg.append("Togg ID hesabınız bağlı. Canlı araç verilerinize (şarj, menzil, lastik vb.) ulaşmak için lütfen Trumore uygulamasındaki 'Akıllı Cihazım' sekmesinde veya ruhsatınızda yer alan 17 haneli şasi numaranızı (NL1...) giriniz:");
        builder.setMessage(msg.toString());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8));

        final EditText etVinInput = new EditText(this);
        etVinInput.setHint("Örn: NL1CFB0P800100522");
        String currentVin = storage.getVin();
        if (storage.isDemoVin()) {
            etVinInput.setText("NL1");
        } else {
            etVinInput.setText(currentVin);
        }
        etVinInput.setSelection(etVinInput.getText().length());
        etVinInput.setAllCaps(true);
        etVinInput.setSingleLine(true);
        etVinInput.setTextSize(16);
        layout.addView(etVinInput);

        builder.setView(layout);
        builder.setPositiveButton("Kaydet ve Bağlan", (dialog, which) -> {
            String newVin = etVinInput.getText().toString().trim().toUpperCase();
            if (newVin.length() < 11) {
                Toast.makeText(this, "Lütfen geçerli bir şasi numarası (17 hane) giriniz.", Toast.LENGTH_LONG).show();
                return;
            }
            storage.setVin(newVin);
            storage.setDemoMode(false);
            Toast.makeText(this, "Şasi numarası kaydedildi: " + newVin, Toast.LENGTH_SHORT).show();
            refreshTelemetry();
        });
        builder.setNegativeButton("Daha Sonra", null);
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialogInstance = builder.create();

        TextView tvAuthStatus = dialogView.findViewById(R.id.tv_auth_status);
        Button btnToggLogin = dialogView.findViewById(R.id.btn_togg_login);
        Button btnToggLogout = dialogView.findViewById(R.id.btn_togg_logout);
        EditText etPlate = dialogView.findViewById(R.id.et_plate);
        EditText etVin = dialogView.findViewById(R.id.et_vin);
        EditText etToken = dialogView.findViewById(R.id.et_token);
        CheckBox cbDemoMode = dialogView.findViewById(R.id.cb_demo_mode);

        boolean hasAuth = storage.hasValidToken();
        if (tvAuthStatus != null) {
            if (hasAuth) {
                tvAuthStatus.setText("Durum: ✅ Togg ID Bağlı (Token Aktif)");
                tvAuthStatus.setTextColor(getColor(R.color.status_green));
                if (btnToggLogin != null) btnToggLogin.setText("🔄 Togg ID Oturumunu Yenile");
                if (btnToggLogout != null) btnToggLogout.setVisibility(View.VISIBLE);
            } else {
                tvAuthStatus.setText("Durum: ⚠️ Bağlı Değil (Simülasyon Modu)");
                tvAuthStatus.setTextColor(getColor(R.color.status_amber));
                if (btnToggLogin != null) btnToggLogin.setText("🔑 Togg ID ile Giriş Yap");
                if (btnToggLogout != null) btnToggLogout.setVisibility(View.GONE);
            }
        }

        if (btnToggLogin != null) {
            btnToggLogin.setOnClickListener(v -> {
                dialogInstance.dismiss();
                launchToggLogin();
            });
        }

        if (btnToggLogout != null) {
            btnToggLogout.setOnClickListener(v -> {
                storage.clearTokens();
                storage.setDemoMode(true);
                Toast.makeText(this, "Togg ID oturumu kapatıldı.", Toast.LENGTH_SHORT).show();
                dialogInstance.dismiss();
                refreshTelemetry();
            });
        }

        etPlate.setText(storage.getPlate());
        etVin.setText(storage.getVin());
        etToken.setText(storage.getAccessToken());
        cbDemoMode.setChecked(storage.isDemoMode());

        dialogView.findViewById(R.id.et_plate).post(() -> {
            // Dialog buttons
        });

        // Set action buttons on the dialog instance
        dialogInstance.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.btn_save), (dialog, which) -> {
            String enteredToken = etToken.getText().toString().trim();
            if (enteredToken.contains("@")) {
                Toast.makeText(this, "E-posta adresi token değildir. Bu alanı boş bırakabilirsiniz.", Toast.LENGTH_LONG).show();
                enteredToken = "";
            }

            storage.setPlate(etPlate.getText().toString().trim());
            storage.setVin(etVin.getText().toString().trim());
            if (!enteredToken.isEmpty()) {
                storage.setAccessToken(enteredToken);
            }
            storage.setDemoMode(cbDemoMode.isChecked() || !storage.hasValidToken());
            refreshTelemetry();
        });

        dialogInstance.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.btn_cancel), (dialog, which) -> dialog.dismiss());
        dialogInstance.show();
    }

    private String formatSyncTime(long millis) {
        if (millis <= 0) return "--:--";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
