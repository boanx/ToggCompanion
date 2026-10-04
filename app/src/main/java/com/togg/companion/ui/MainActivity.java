package com.togg.companion.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.PixelCopy;
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
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RadioButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.io.File;
import java.io.FileOutputStream;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.togg.companion.R;
import com.togg.companion.core.DigitalKey;
import com.togg.companion.core.GezRouteBridge;
import com.togg.companion.core.DigitalKeyManager;
import com.togg.companion.core.TelemetryParser;
import com.togg.companion.core.TrumoreBridge;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.core.VehicleBleManager;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.data.remote.AbrpClient;
import com.togg.companion.service.AbrpJobService;
import com.togg.companion.wearable.WearSyncManager;
import com.togg.companion.widget.ToggAppWidgetProvider;

public final class MainActivity extends Activity {
    public static final String ACTION_WIDGET_REFRESH = "com.togg.companion.widget.OPEN_AND_REFRESH";

    private static MainActivity instance;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        SecureTokenStorage prefs = new SecureTokenStorage(newBase);
        String theme = prefs.getAppTheme();
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        if ("dark".equals(theme)) config.uiMode = (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | android.content.res.Configuration.UI_MODE_NIGHT_YES;
        else if ("light".equals(theme)) config.uiMode = (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | android.content.res.Configuration.UI_MODE_NIGHT_NO;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

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
    private TextView tvAppRefreshTime;
    private TextView tvSyncStatus;

    // 7 Tabs Navigation Elements
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

    // Interactive State
    private boolean isDoorLocked = true;
    private boolean isTrunkOpen = false;
    private boolean isClimateActive = false;
    private boolean isDefrostActive = false;
    private boolean isHeadlightsOn = false;
    private double currentTargetTemp = 21.5;
    private VehicleTelemetry currentTelemetry;

    // Quick Actions Bar
    private LinearLayout llQuickActionsRow;
    private View emotorReadyCard;
    private TextView tvEmotorState;
    private ImageView ivEmotorIcon;
    private TextView tvBleStatus;
    private VehicleBleManager bleManager;
    private final VehicleBleManager.OnBleStateChangeListener bleListener = state -> updateBleStatus();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try { WebView.enableSlowWholeDocumentDraw(); } catch (Throwable ignored) {}
        super.onCreate(savedInstanceState);
        instance = this;
        storage = new SecureTokenStorage(this);
        bleManager = VehicleBleManager.getInstance(this);
        bleManager.addListener(bleListener);
        applyThemeBeforeContent();
        setContentView(R.layout.activity_main);
        dbHelper = new TelemetryDatabaseHelper(this);
        apiClient = new TvaApiClient(storage);

        bindViews();
        setupWindowInsets();
        setupSwipeRefresh();
        setupListeners();
        updateBleStatus();

        // Default to Tab 0 (Ana Sayfa)
        selectTab(0);

        isHeadlightsOn = storage.isHeadlightsOn();
        renderQuickActionsBar();
        updateEmotorCard();

        // Load cached telemetry if present, otherwise fetch fresh/demo data
        VehicleTelemetry cached = dbHelper.getLatest();
        if (cached != null) {
            if (cached.latitude != 0.0 && (cached.latitude != 39.925054 || !storage.hasSavedLocation())) {
                storage.saveLastLocation(cached.latitude, cached.longitude, cached.parkAddress);
            } else if (storage.hasSavedLocation()) {
                cached = cached.withLocation(storage.getLastLat(), storage.getLastLon(), storage.getLastAddress());
            }
            currentTelemetry = cached;
            isDoorLocked = cached.isLocked;
            isTrunkOpen = cached.trunkOpen;
            isHeadlightsOn = cached.headlightsOn;
            isClimateActive = cached.isClimateActive;
            currentTargetTemp = cached.targetHvacCelsius > 0 ? cached.targetHvacCelsius : 21.5;
            renderTelemetry(cached, storage.isDemoMode());
            updateHeroCarVisual();
            renderQuickActionsBar();
            ToggAppWidgetProvider.updateAllWidgets(this);
        } else if (storage.hasSavedLocation()) {
            VehicleTelemetry demo = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
            demo = demo.withLocation(storage.getLastLat(), storage.getLastLon(), storage.getLastAddress());
            currentTelemetry = demo;
            renderTelemetry(demo, storage.isDemoMode());
            updateHeroCarVisual();
            renderQuickActionsBar();
        }
        refreshTelemetry();

        handleDigitalKeyIntent(getIntent());
        handleGezShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && ACTION_WIDGET_REFRESH.equals(intent.getAction())) refreshTelemetry();
        handleActionIntent(intent);
        handleDigitalKeyIntent(intent);
        handleGezShareIntent(intent);
    }

    private void handleGezShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.trim().isEmpty()) showSendToGezDialog(sharedText);
        }
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
        tvVehicleTitle.setText(storage.getVehicleName());
        tvVehicleTitle.setClickable(true);
        tvVehicleTitle.setFocusable(true);
        tvVehicleTitle.setOnClickListener(v -> showEditVehicleNameDialog());
        tvVehiclePlate = findViewById(R.id.tv_vehicle_plate);
        tvVehicleVin = findViewById(R.id.tv_vehicle_vin);
        tvAppRefreshTime = findViewById(R.id.tv_app_refresh_time);
        tvSyncStatus = findViewById(R.id.tv_sync_status);

        // 7 Tab Selector Buttons (Fixed at Top)
        tabButtons = new TextView[]{
                findViewById(R.id.tab_home),
                findViewById(R.id.tab_battery),
                findViewById(R.id.tab_aux_battery),
                findViewById(R.id.tab_tires),
                findViewById(R.id.tab_security),
                findViewById(R.id.tab_drive),
                findViewById(R.id.tab_location)
        };

        // 7 Corresponding Page Containers
        tabPages = new View[]{
                findViewById(R.id.page_home),
                findViewById(R.id.page_battery),
                findViewById(R.id.page_aux_battery),
                findViewById(R.id.page_tires),
                findViewById(R.id.page_security),
                findViewById(R.id.page_drive),
                findViewById(R.id.page_location)
        };

        // Page 0: Ana Sayfa
        homeCardsContainer = findViewById(R.id.home_cards_container);
        btnCustomizeHome = null;

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
        tvDriveMode.setOnClickListener(v -> showDriveModeSelector());
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

        // Page 0: Ana Sayfa
        homeCardsContainer = findViewById(R.id.home_cards_container);
        btnCustomizeHome = null;
        View legacyDigitalKeyCard = findViewById(R.id.card_digital_key);
        if (legacyDigitalKeyCard != null) legacyDigitalKeyCard.setVisibility(View.GONE);
        llQuickActionsRow = findViewById(R.id.ll_quick_actions_row);
        emotorReadyCard = findViewById(R.id.card_emotor_ready);
        tvEmotorState = findViewById(R.id.tv_emotor_state);
        ivEmotorIcon = findViewById(R.id.iv_emotor_icon);
        tvBleStatus = findViewById(R.id.tv_ble_status);
        tvBleStatus.setOnClickListener(v -> showBleConnectionDialog());
        emotorReadyCard.setOnClickListener(v -> toggleReadyToDrive());

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

        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsDialog());

        if (tvVehicleVin != null) {
            tvVehicleVin.setOnClickListener(v -> showVinInputDialog(null));
        }
        if (tvAppRefreshTime != null) {
            tvAppRefreshTime.setOnClickListener(v -> showVinInputDialog(null));
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
        double lat = currentTelemetry != null ? currentTelemetry.latitude : storage.getLastLat();
        double lon = currentTelemetry != null ? currentTelemetry.longitude : storage.getLastLon();
        if (lat == 0.0 && lon == 0.0) {
            lat = storage.getLastLat();
            lon = storage.getLastLon();
        }
        if (lat == 0.0 && lon == 0.0) {
            lat = 39.925054;
            lon = 32.834755;
        }
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

    public void toggleHeadlights() {
        isHeadlightsOn = !isHeadlightsOn;
        storage.setHeadlightsOn(isHeadlightsOn);
        if (currentTelemetry != null) {
            currentTelemetry = currentTelemetry.withHeadlights(isHeadlightsOn);
            dbHelper.insert(currentTelemetry);
        }
        updateHeroCarVisual();
        renderQuickActionsBar();
        Toast.makeText(this, isHeadlightsOn ? "💡 Farlar Açıldı" : "💡 Farlar Kapatıldı", Toast.LENGTH_SHORT).show();
    }

    private void updateHeroCarVisual() {
        // The former hero car overlays are no longer part of the dashboard layout.
        updateEmotorCard();
    }

    private void renderQuickActionsBar() {
        if (llQuickActionsRow == null) return;
        llQuickActionsRow.removeAllViews();
        String[] keys = {"lock", "trunk"};
        String[] labels = {"Kap\u0131 Kilidi", "Bagaj"};
        String[] icons = {"\uD83D\uDD12", "\uD83D\uDE97"};
        String[] actions = storage.getQuickActionsOrder().split(",");
        for (String raw : actions) {
            String act = raw.trim().toLowerCase(Locale.ROOT);
            int index = Arrays.asList(keys).indexOf(act);
            if (index < 0) continue;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(dpToPx(62), ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.setMargins(dpToPx(16), 0, dpToPx(16), 0); item.setLayoutParams(itemLp);
            TextView icon = new TextView(this); icon.setText(icons[index]); icon.setTextSize(21); icon.setGravity(Gravity.CENTER);
            icon.setBackgroundResource(R.drawable.btn_action_circle);
            item.addView(icon, new LinearLayout.LayoutParams(dpToPx(52), dpToPx(52)));
            TextView label = new TextView(this); label.setText(labels[index]); label.setTextSize(9); label.setTextColor(getColor(R.color.text_secondary)); label.setGravity(Gravity.CENTER); label.setPadding(0, dpToPx(3), 0, 0);
            item.addView(label);
            item.setOnClickListener(v -> dispatchQuickAction(act));
            llQuickActionsRow.addView(item);
        }
        updateEmotorCard();
    }

    private void updateEmotorCard() {
        boolean connected = bleManager != null && bleManager.isConnected();
        boolean ready = currentTelemetry != null && currentTelemetry.isReadyToDrive;
        int color;
        String state;
        if (!connected) {
            color = 0xFF94A3B8;
            state = "Bluetooth Bağlı Değil";
        } else if (!ready) {
            color = 0xFFFFB800;
            state = "Aksesuar Modu";
        } else {
            color = 0xFF10B981;
            state = "Sürüş Modu (READY)";
        }
        if (tvEmotorState != null) {
            tvEmotorState.setText(state);
            tvEmotorState.setTextColor(color);
        }
        if (ivEmotorIcon != null) ivEmotorIcon.setColorFilter(color);
    }

    private void dispatchQuickAction(String action) {
        switch (action) {
            case "lock": toggleDoorLock(); break;
            case "headlights": toggleHeadlights(); break;
            case "trunk": confirmAndToggleTrunk(); break;
            case "climate": toggleClimate(); break;
            case "defrost": toggleDefrost(); break;
            case "locate": openNavigationToCar(); break;
            case "drive_mode": showDriveModeSelector(); break;
            default: Toast.makeText(this, action + " komutu gï¿½nderildi.", Toast.LENGTH_SHORT).show(); break;
        }
    }

    private void showQuickActionsCustomizerDialog() {
        final String[] keys = {"lock", "trunk"};
        final String[] labels = {"Kap\u0131 Kilidi \uD83D\uDD12", "Bagaj \uD83D\uDE97"};
        Set<String> selected = new HashSet<>(Arrays.asList(storage.getQuickActionsOrder().split(",")));
        boolean[] checked = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) checked[i] = selected.contains(keys[i]);
        new AlertDialog.Builder(this).setTitle("Hızlı İşlemleri Düzenle").setMultiChoiceItems(labels, checked, (d, which, value) -> checked[which] = value)
                .setPositiveButton("Kaydet", (d, which) -> { StringBuilder order = new StringBuilder(); for (int i = 0; i < keys.length; i++) if (checked[i]) { if (order.length() > 0) order.append(','); order.append(keys[i]); } storage.setQuickActionsOrder(order.toString()); renderQuickActionsBar(); })
                .setNegativeButton("İptal", null).show();
    }

    static class ActionItem {
        final String key;
        final String title;
        ActionItem(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }

    private void showQuickActionsReorderDialog() {
        if (isFinishing() || isDestroyed()) return;

        String orderStr = storage.getQuickActionsOrder();
        List<String> currentOrder = new ArrayList<>(Arrays.asList(orderStr.split(",")));
        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("emotor", "⚡ e-Motor");
        nameMap.put("headlights", "💡 Far Kontrolü");
        nameMap.put("lock", "🔒 Kapı Kilidi");
        nameMap.put("trunk", "🚗 Bagaj Kapağı");
        nameMap.put("climate", "❄️ Klima Aç/Kapat");
        nameMap.put("defrost", "♨️ Ön Cam Buğu");
        nameMap.put("locate", "🧭 Haritada Aracı Bul");

        List<ActionItem> items = new ArrayList<>();
        for (String k : currentOrder) {
            String key = k.trim().toLowerCase(Locale.ROOT);
            if (nameMap.containsKey(key)) {
                items.add(new ActionItem(key, nameMap.get(key)));
            }
        }
        for (Map.Entry<String, String> e : nameMap.entrySet()) {
            boolean exists = false;
            for (ActionItem it : items) {
                if (it.key.equals(e.getKey())) { exists = true; break; }
            }
            if (!exists) items.add(new ActionItem(e.getKey(), e.getValue()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚡ Hızlı Komut Buton Sıralaması");

        ScrollView sv = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));

        Runnable[] refreshList = new Runnable[1];
        refreshList[0] = () -> {
            listLayout.removeAllViews();
            for (int i = 0; i < items.size(); i++) {
                final int pos = i;
                ActionItem it = items.get(pos);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dpToPx(6), 0, dpToPx(6));

                TextView tv = new TextView(this);
                tv.setText((pos + 1) + ". " + it.title);
                tv.setTextSize(14);
                tv.setTextColor(getColor(R.color.text_primary));
                LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                tv.setLayoutParams(tvLp);
                row.addView(tv);

                if (pos > 0) {
                    Button upBtn = new Button(this);
                    upBtn.setText("▲");
                    upBtn.setTextSize(12);
                    upBtn.setBackgroundResource(R.drawable.badge_pill);
                    upBtn.setTextColor(getColor(R.color.accent_togg));
                    LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(36));
                    bLp.setMarginEnd(dpToPx(4));
                    upBtn.setLayoutParams(bLp);
                    upBtn.setOnClickListener(v -> {
                        Collections.swap(items, pos, pos - 1);
                        refreshList[0].run();
                    });
                    row.addView(upBtn);
                }

                if (pos < items.size() - 1) {
                    Button downBtn = new Button(this);
                    downBtn.setText("▼");
                    downBtn.setTextSize(12);
                    downBtn.setBackgroundResource(R.drawable.badge_pill);
                    downBtn.setTextColor(getColor(R.color.accent_togg));
                    LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(36));
                    downBtn.setLayoutParams(bLp);
                    downBtn.setOnClickListener(v -> {
                        Collections.swap(items, pos, pos + 1);
                        refreshList[0].run();
                    });
                    row.addView(downBtn);
                }

                listLayout.addView(row);
            }
        };

        refreshList[0].run();
        sv.addView(listLayout);
        builder.setView(sv);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(items.get(i).key);
            }
            storage.setQuickActionsOrder(sb.toString());
            renderQuickActionsBar();
            Toast.makeText(this, "Buton yerleşimi kaydedildi", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    private void toggleDoorLock() {
        sendVehicleCommand(isDoorLocked ? VehicleBleManager.VehicleCommand.UNLOCK : VehicleBleManager.VehicleCommand.LOCK);
    }

    private boolean requireBleConnection() {
        if (bleManager.isConnected()) return true;
        new AlertDialog.Builder(this).setTitle("Bluetooth Bağlantısı Gerekli")
                .setMessage("Togg güvenlik protokolü gereği kapı kilidi, bagaj ve e-motor komutları yalnızca araç ile Bluetooth bağlantısı kurulduğunda çalışır.\n\nLütfen önce aracınıza Bluetooth ile bağlanın.")
                .setPositiveButton("Araca Bağlan", (d, w) -> showBleConnectionDialog())
                .setNegativeButton("İptal", null).show();
        return false;
    }

    private void sendVehicleCommand(VehicleBleManager.VehicleCommand command) {
        if (!requireBleConnection()) return;
        bleManager.setTelemetry(currentTelemetry);
        Toast.makeText(this, "Komut araca iletiliyor...", Toast.LENGTH_SHORT).show();
        bleManager.sendCommand(command, (success, message, updated) -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (!success || updated == null) return;
            currentTelemetry = updated;
            isDoorLocked = updated.isLocked;
            isTrunkOpen = updated.trunkOpen;
            dbHelper.insert(updated);
            storage.saveActiveTelemetry(updated);
            updateDoorUi();
            updateTrunkUi();
            updateHeroCarVisual();
            renderQuickActionsBar();
            ToggAppWidgetProvider.updateAllWidgets(this);
            renderHomeCards();
            WearSyncManager.syncTelemetry(this, updated);
        });
    }

    private void updateBleStatus() {
        updateEmotorCard();
        if (tvBleStatus == null) return;
        if (bleManager.isConnected()) {
            tvBleStatus.setText("📶 Bluetooth: Bağlı (" + bleManager.getConnectedDeviceName() + ")");
            tvBleStatus.setTextColor(getColor(R.color.status_green));
        } else if (bleManager.getConnectionState() == VehicleBleManager.ConnectionState.CONNECTING) {
            tvBleStatus.setText("⏳ Bluetooth: Araca Bağlanıyor...");
            tvBleStatus.setTextColor(getColor(R.color.status_amber));
        } else {
            tvBleStatus.setText("⭕ Bluetooth: Bağlı Değil (Dokun & Bağlan)");
            tvBleStatus.setTextColor(getColor(R.color.text_secondary));
        }
    }

    private void showBleConnectionDialog() {
        boolean needsPermission = Build.VERSION.SDK_INT >= 31 && (!bleManager.hasConnectPermission()
                || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED);
        if (needsPermission) {
            new AlertDialog.Builder(this).setTitle("Bluetooth İzni Gerekli")
                    .setMessage("Eşleşmiş cihazları görmek ve BLE taraması için Bluetooth izinleri gerekli.")
                    .setPositiveButton("Bluetooth İzinlerini Ver", (d, w) -> requestPermissions(
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 301))
                    .setNegativeButton("İptal", null).show();
            return;
        }
        List<BluetoothDevice> devices = bleManager.getPairedToggDevices();
        List<BluetoothDevice> allDevices = bleManager.getAllPairedDevices();
        List<BluetoothDevice> otherDevices = new ArrayList<>();
        List<String> options = new ArrayList<>();
        for (BluetoothDevice device : devices) options.add(device.getName() + (bleManager.isDeviceConnected(device) ? " 🟢 [Bağlı]" : " [Eşleşmiş]"));
        int scanIndex = options.size(); options.add("🔍 Çevredeki BLE Cihazlarını Tara");
        for (BluetoothDevice device : allDevices) if (!devices.contains(device)) otherDevices.add(device);
        int otherIndex = -1;
        if (!otherDevices.isEmpty()) { otherIndex = options.size(); options.add("📱 Diğer Eşleşmiş Bluetooth Cihazları"); }
        int demoIndex = -1;
        if (storage.isDemoMode()) { demoIndex = options.size(); options.add("Demo BLE Bağlantısı"); }
        int disconnectIndex = -1;
        if (bleManager.isConnected()) { disconnectIndex = options.size(); options.add("Bağlantıyı Kes"); }
        int openIndex = -1;
        if (!bleManager.isBluetoothEnabled()) { openIndex = options.size(); options.add("Bluetooth'u Aç"); }
        final int pairedCount = devices.size(), scanChoice = scanIndex, othersChoice = otherIndex;
        final int demoChoice = demoIndex, disconnectChoice = disconnectIndex, openChoice = openIndex;
        new AlertDialog.Builder(this).setTitle("Araç Bluetooth Bağlantısı")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which < pairedCount) bleManager.connectToVehicle(this, devices.get(which));
                    else if (which == scanChoice) {
                        Toast.makeText(this, "Çevredeki Togg BLE cihazları taranıyor...", Toast.LENGTH_SHORT).show();
                        bleManager.scanForToggDevices(found -> {
                            List<String> names = new ArrayList<>();
                            for (BluetoothDevice item : found) names.add(item.getName() == null ? item.getAddress() : item.getName());
                            if (names.isEmpty()) { Toast.makeText(this, "Togg BLE cihazı bulunamadı.", Toast.LENGTH_SHORT).show(); return; }
                            new AlertDialog.Builder(this).setTitle("Bulunan Togg BLE Cihazları").setItems(names.toArray(new String[0]),
                                    (d, choice) -> bleManager.connectToVehicle(this, found.get(choice))).setNegativeButton("İptal", null).show();
                        });
                    } else if (which == othersChoice) {
                        List<String> names = new ArrayList<>();
                        for (BluetoothDevice item : otherDevices) names.add(item.getName() + (bleManager.isDeviceConnected(item) ? " 🟢 [Bağlı]" : " [Eşleşmiş]"));
                        new AlertDialog.Builder(this).setTitle("Eşleşmiş Bluetooth Cihazları").setItems(names.toArray(new String[0]),
                                (d, choice) -> bleManager.connectToVehicle(this, otherDevices.get(choice))).setNegativeButton("İptal", null).show();
                    } else if (which == demoChoice) bleManager.setDemoConnected(true);
                    else if (which == disconnectChoice) bleManager.disconnect();
                    else if (which == openChoice) startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                }).setNegativeButton("İptal", null).show();
    }    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 301 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showBleConnectionDialog();
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
        if (!requireBleConnection()) return;
        new AlertDialog.Builder(this).setTitle(R.string.trunk_confirm_title)
                .setMessage(R.string.trunk_confirm_msg)
                .setPositiveButton("Evet, " + (isTrunkOpen ? "Kapat" : "Aç"), (d, w) -> sendVehicleCommand(VehicleBleManager.VehicleCommand.TRUNK))
                .setNegativeButton(R.string.btn_cancel, null).show();
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
        updateHeroCarVisual();
    }

    private void toggleClimate() {
        isClimateActive = !isClimateActive;
        updateClimateUi();
        updateHeroCarVisual();
        renderQuickActionsBar();

        if (isClimateActive) {
            Toast.makeText(this, "Togg T10F: İklimlendirme Başlatıldı (" + String.format(Locale.ROOT, "%.1f°C", currentTargetTemp) + ") ❄️", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Togg T10F: İklimlendirme Kapatıldı", Toast.LENGTH_SHORT).show();
        }

        if (currentTelemetry != null) {
            currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, isClimateActive, isDefrostActive);
            dbHelper.insert(currentTelemetry);
            renderHomeCards();
            WearSyncManager.syncTelemetry(this, currentTelemetry);
        }
    }

    private void toggleDefrost() {
        isDefrostActive = !isDefrostActive;
        updateClimateUi();
        updateHeroCarVisual();
        renderQuickActionsBar();

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
        swipeRefresh.setRefreshing(true);
        tvSyncStatus.setText("GÜNCELLENİYOR...");

        apiClient.fetchTelemetry(new TvaApiClient.TelemetryCallback() {
            @Override
            public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                final VehicleTelemetry updatedTelemetry = telemetry != null && currentTelemetry != null
                        ? telemetry.withReadyToDrive(currentTelemetry.isReadyToDrive) : telemetry;
                currentTelemetry = updatedTelemetry;
                dbHelper.insert(updatedTelemetry);
                storage.saveActiveTelemetry(updatedTelemetry);
                maybeSendAbrp(updatedTelemetry);
                ToggAppWidgetProvider.updateAllWidgets(MainActivity.this);
                storage.setLastRefresh(System.currentTimeMillis());
                if (updatedTelemetry.timestampMillis > 0) {
                    storage.setLastSync(updatedTelemetry.timestampMillis);
                }
                runOnUiThread(() -> {
                    renderTelemetry(updatedTelemetry, !isLive);
                    swipeRefresh.setRefreshing(false);
                    if (isLive) {
                        Toast.makeText(MainActivity.this, "Canlı telemetri alındı", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message, VehicleTelemetry fallback) {
                currentTelemetry = fallback;
                if (fallback != null) dbHelper.insert(fallback);
                ToggAppWidgetProvider.updateAllWidgets(MainActivity.this);
                runOnUiThread(() -> {
                    renderTelemetry(fallback, true);
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
        storage.saveActiveTelemetry(t);
        ToggAppWidgetProvider.updateAllWidgets(this);

        // Vehicle Header with VIN and Last Sync Timestamp
        tvVehicleTitle.setText(storage.getVehicleName());
        tvVehiclePlate.setText(t.plate != null && !t.plate.isEmpty() ? t.plate : getString(R.string.plate_placeholder));
        if (tvVehicleVin != null) {
            long vehicleSync = t.timestampMillis;
            String syncLabel = vehicleSync > 0 ? formatTrumoreSyncTime(vehicleSync) : "--:--";
            tvVehicleVin.setText("Akıllı Cihaz ile son senkronizasyon: " + syncLabel);
        }
        if (tvAppRefreshTime != null) {
            long appRefresh = storage.getLastRefresh();
            tvAppRefreshTime.setText("Son Yenileme: " + (appRefresh > 0 ? formatSyncTime(appRefresh) : "--:--"));
        }
        tvSyncStatus.setText(isDemo ? getString(R.string.status_demo) : getString(R.string.status_live));
        updateEmotorCard();

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
        tvOdometer.setText(t.odometerKm + " km");
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
        if ("emotor".equalsIgnoreCase(action) || "start_drive".equalsIgnoreCase(action)) {
            toggleReadyToDrive();
            return;
        }
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
            updateHeroCarVisual();
            renderQuickActionsBar();
            Toast.makeText(this, "🔑 Dijital Anahtar: Kapılar kilitlendi 🔒 (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
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
            updateHeroCarVisual();
            renderQuickActionsBar();
            Toast.makeText(this, "🔑 Dijital Anahtar: Kapı kilitleri açıldı 🔓 (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withLock(false);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        } else if ("trunk".equalsIgnoreCase(action)) {
            if (!activeKey.canTrunk) {
                Toast.makeText(this, "Bu anahtarın bagaj kapağını açma yetkisi yok!", Toast.LENGTH_SHORT).show();
                return;
            }
            isTrunkOpen = !isTrunkOpen;
            updateTrunkUi();
            updateHeroCarVisual();
            renderQuickActionsBar();
            Toast.makeText(this, "🔑 Dijital Anahtar: Bagaj " + (isTrunkOpen ? "açıldı 📦" : "kapatıldı") + " (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withTrunk(isTrunkOpen);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        } else if ("climate_start".equalsIgnoreCase(action) || "climate".equalsIgnoreCase(action)) {
            if (!activeKey.canClimate) {
                Toast.makeText(this, "Bu anahtarın iklimlendirme yetkisi yok!", Toast.LENGTH_SHORT).show();
                return;
            }
            isClimateActive = true;
            updateClimateUi();
            updateHeroCarVisual();
            renderQuickActionsBar();
            Toast.makeText(this, "🔑 Dijital Anahtar: İklimlendirme başlatıldı ❄️ (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, true, isDefrostActive);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        } else if ("climate_stop".equalsIgnoreCase(action)) {
            isClimateActive = false;
            updateClimateUi();
            updateHeroCarVisual();
            renderQuickActionsBar();
            Toast.makeText(this, "🔑 Dijital Anahtar: İklimlendirme durduruldu (" + activeKey.seId + ")", Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, false, isDefrostActive);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        } else if ("defrost".equalsIgnoreCase(action)) {
            isDefrostActive = !isDefrostActive;
            updateClimateUi();
            updateHeroCarVisual();
            renderQuickActionsBar();
            Toast.makeText(this, "🔑 Dijital Anahtar: Ön cam buğu çözme " + (isDefrostActive ? "açıldı ♨️" : "kapatıldı"), Toast.LENGTH_SHORT).show();
            if (currentTelemetry != null) {
                currentTelemetry = currentTelemetry.withClimate(currentTargetTemp, isClimateActive, isDefrostActive);
                dbHelper.insert(currentTelemetry);
                renderHomeCards();
                WearSyncManager.syncTelemetry(this, currentTelemetry);
            }
        } else if ("headlights".equalsIgnoreCase(action)) {
            toggleHeadlights();
        }
    }

    private void toggleReadyToDrive() {
        if (!requireBleConnection()) return;
        if (currentTelemetry == null) currentTelemetry = dbHelper.getLatest();
        if (currentTelemetry == null) return;
        sendVehicleCommand(currentTelemetry.isReadyToDrive ? VehicleBleManager.VehicleCommand.EMOTOR_STOP : VehicleBleManager.VehicleCommand.EMOTOR_START);
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
            selectTab(4); // Switch to Security tab
            Toast.makeText(this, "Dijital anahtar başarıyla kaydedildi ve akıllı saate aktarıldı! ⌚🔑", Toast.LENGTH_LONG).show();
        });

        builder.setNeutralButton("Sadece Telefona Kaydet", (dialog, which) -> {
            DigitalKeyManager mgr = new DigitalKeyManager(this);
            mgr.saveKey(key);
            renderDigitalKeyCard();
            renderHomeCards();
            selectTab(4);
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
            tvTripDist.setText(String.format(Locale.getDefault(), "%d km", Math.round(trip.distanceKm)));
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

    private void showDriveModeSelector() {
        new AlertDialog.Builder(this)
                .setTitle("Sürüş Modu")
                .setItems(new String[]{"Eco", "Konfor", "Spor"}, (dialog, which) -> {
                    String[] modes = {"Eco", "Comfort", "Sport"};
                    activateDriveMode(modes[which]);
                })
                .show();
    }

    private void activateDriveMode(String mode) {
        if (currentTelemetry == null) return;
        currentTelemetry = currentTelemetry.withDriveMode(mode);
        dbHelper.insert(currentTelemetry);
        renderTelemetry(currentTelemetry, storage.isDemoMode());
        String displayMode = "Eco".equals(mode) ? "Eco" : ("Sport".equals(mode) ? "Spor" : "Konfor");
        Toast.makeText(this, "Togg T10F: Sürüş Modu -> " + displayMode + ("Sport".equals(mode) ? " ⚡" : ""), Toast.LENGTH_SHORT).show();
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
                        storage.saveLastLocation(lat, lon, finalRes);
                        if (currentTelemetry != null) {
                            currentTelemetry = currentTelemetry.withLocation(lat, lon, finalRes);
                            storage.saveActiveTelemetry(currentTelemetry);
                            dbHelper.insert(currentTelemetry);
                        }
                        ToggAppWidgetProvider.updateAllWidgets(MainActivity.this);
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

        List<View> gridCards = new ArrayList<>();
        View fullWidthLocationCard = null;

        for (String cardKey : order) {
            String key = cardKey.trim();
            if (CARD_DIGITAL_KEY.equals(key)) continue;
            if (CARD_CONTROLS.equals(key) || CARD_CLIMATE.equals(key)) continue;
            if (!visibleSet.contains(key)) continue;

            if (CARD_LOCATION.equals(key)) {
                fullWidthLocationCard = buildLocationCard(t);
            } else {
                View card = buildHomeCard(key, t);
                if (card != null) {
                    gridCards.add(card);
                }
            }
        }

        // Pair grid cards into 2-column adaptive rows (max 2 cards per row)
        for (int i = 0; i < gridCards.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setWeightSum(2.0f);

            View card1 = gridCards.get(i);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            p1.bottomMargin = dpToPx(12);
            if (i + 1 < gridCards.size()) {
                p1.setMarginEnd(dpToPx(6));
            }
            card1.setLayoutParams(p1);
            row.addView(card1);

            if (i + 1 < gridCards.size()) {
                View card2 = gridCards.get(i + 1);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                p2.bottomMargin = dpToPx(12);
                p2.setMarginStart(dpToPx(6));
                card2.setLayoutParams(p2);
                row.addView(card2);
            }

            homeCardsContainer.addView(row);
        }

        // In-App Embedded Google Maps Location Card spans full width
        if (fullWidthLocationCard != null) {
            homeCardsContainer.addView(fullWidthLocationCard);
        }
        homeCardsContainer.addView(buildAbrpCard(t));
    }

    private View buildAbrpCard(VehicleTelemetry t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(14);
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showAbrpSettingsDialog());

        TextView title = new TextView(this);
        title.setText("🗺️ ABRP Canlı Rota Planlayıcı");
        title.setTextColor(getColor(R.color.accent_togg));
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView status = new TextView(this);
        status.setText(storage.isAbrpEnabled() ? "🟢 7 dk Senkronizasyon Açık" : "⚪ Senkronizasyon Kapalı (Kurulum için dokunun)");
        status.setTextColor(getColor(R.color.text_secondary));
        status.setTextSize(12);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dpToPx(6);
        card.addView(status, statusParams);

        TextView telemetry = new TextView(this);
        String consumption = t.lastTripAvgConsumption > 0
                ? String.format(Locale.US, "%.1f", t.lastTripAvgConsumption) : "16.8";
        telemetry.setText("%" + t.batterySocPercent + " Batarya  •  " + consumption
                + " kWh/100km  •  " + t.estimatedRangeKm + " km menzil");
        telemetry.setTextColor(getColor(R.color.text_primary));
        telemetry.setTextSize(13);
        LinearLayout.LayoutParams telemetryParams = new LinearLayout.LayoutParams(-1, -2);
        telemetryParams.topMargin = dpToPx(8);
        card.addView(telemetry, telemetryParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dpToPx(10);
        card.addView(buttons, buttonsParams);

        Button send = new Button(this);
        send.setText("⚡ Şimdi Gönder");
        send.setTextSize(11);
        send.setOnClickListener(v -> sendAbrpTelemetry(currentTelemetry != null ? currentTelemetry : t, storage.getAbrpToken(), storage.getAbrpCapacity()));
        Button open = new Button(this);
        open.setText("🗺️ ABRP'de Aç");
        open.setTextSize(11);
        open.setOnClickListener(v -> openAbrp());
        Button settings = new Button(this);
        settings.setText("⚙️ Ayarlar");
        settings.setTextSize(11);
        settings.setOnClickListener(v -> showAbrpSettingsDialog());
        buttons.addView(send, new LinearLayout.LayoutParams(0, dpToPx(44), 1));
        buttons.addView(open, new LinearLayout.LayoutParams(0, dpToPx(44), 1));
        buttons.addView(settings, new LinearLayout.LayoutParams(0, dpToPx(44), 1));

        Button sendToGez = new Button(this);
        sendToGez.setText("📍 Gez'e / Araca Rota Aktar");
        sendToGez.setBackgroundResource(R.drawable.btn_gradient);
        sendToGez.setTextColor(getColor(android.R.color.white));
        sendToGez.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams gezParams = new LinearLayout.LayoutParams(-1, dpToPx(44));
        gezParams.topMargin = dpToPx(10);
        card.addView(sendToGez, gezParams);
        sendToGez.setOnClickListener(v -> showSendToGezDialog(null));
        return card;
    }

    private void showSendToGezDialog(String prefill) {
        EditText input = new EditText(this);
        input.setHint("ABRP / Google Maps linki veya koordinat");
        String initial = prefill;
        if (initial == null || initial.trim().isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence candidate = clip.getItemAt(0).coerceToText(this);
                    if (candidate != null) initial = candidate.toString();
                }
            }
        }
        if (initial != null) input.setText(initial);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), 0);
        TextView explanation = new TextView(this);
        explanation.setText("Harita bağlantısını veya adresi girin. Hedef çözümlendikten sonra akıllı cihazınıza aktarabilirsiniz.");
        content.addView(explanation);
        content.addView(input);
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
        progress.setIndeterminate(true);
        TextView status = new TextView(this);
        content.addView(progress);
        content.addView(status);
        TextView routeTitle = new TextView(this);
        routeTitle.setTypeface(null, Typeface.BOLD);
        content.addView(routeTitle);
        LinearLayout stopList = new LinearLayout(this);
        stopList.setOrientation(LinearLayout.VERTICAL);
        content.addView(stopList);
        TextView bridgeNote = new TextView(this);
        bridgeNote.setText("ℹ️ Araç navigasyonuna aktarım Togg Trumore Güvenli Köprüsü üzerinden gerçekleştirilmektedir.");
        bridgeNote.setVisibility(View.GONE);
        content.addView(bridgeNote);
        LinearLayout quickButtons = new LinearLayout(this);
        Button paste = new Button(this);
        paste.setText("Panodan Yapıştır");
        paste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) input.setText(clip.getItemAt(0).coerceToText(this));
            }
        });
        quickButtons.addView(paste, new LinearLayout.LayoutParams(0, -2, 1));
        VehicleTelemetry telemetry = currentTelemetry;
        if (telemetry != null && (telemetry.latitude != 0 || telemetry.longitude != 0)) {
            Button vehicleLocation = new Button(this);
            vehicleLocation.setText("Aracın Konumu");
            vehicleLocation.setOnClickListener(v -> input.setText(telemetry.latitude + ", " + telemetry.longitude));
            quickButtons.addView(vehicleLocation, new LinearLayout.LayoutParams(0, -2, 1));
        }
        content.addView(quickButtons);
        Button resolveButton = new Button(this);
        resolveButton.setText("Çözümle");
        content.addView(resolveButton);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rota / Konumu Akıllı Cihaza Aktar")
                .setView(content)
                .setNegativeButton("İptal", null)
                .setPositiveButton("🚀 Varış Noktasını Aktar", null)
                .create();
        final GezRouteBridge.GezPoint[] resolved = new GezRouteBridge.GezPoint[1];
        final int[] selectedStop = {-1};
        Runnable resolve = () -> {
            resolved[0] = null;
            progress.setVisibility(View.VISIBLE);
            status.setText("Hedef adres ve koordinatlar çözümleniyor...");
            GezRouteBridge.resolveAsync(this, input.getText().toString(), new GezRouteBridge.Callback() {
                @Override public void onResolved(GezRouteBridge.GezPoint point) {
                    if (!dialog.isShowing()) return;
                    resolved[0] = point;
                    stopList.removeAllViews();
                    routeTitle.setVisibility(point.hasMultipleStops() ? View.VISIBLE : View.GONE);
                    bridgeNote.setVisibility(point.hasMultipleStops() ? View.VISIBLE : View.GONE);
                    selectedStop[0] = point.hasMultipleStops() ? point.stops.size() - 1 : -1;
                    if (point.hasMultipleStops()) {
                        routeTitle.setText("📍 Çoklu Duraklı Rota (" + point.stops.size() + " Durak):");
                        for (int i = 0; i < point.stops.size(); i++) {
                            final int index = i;
                            GezRouteBridge.GezPoint stop = point.stops.get(i);
                            String label = i == 0 ? "🚩 Başlangıç (1. Durak)" : i == point.stops.size() - 1 ? "🏁 Varış Hedefi" : "🛑 Ara Duraklar (" + (i + 1) + ". Durak)";
                            LinearLayout row = new LinearLayout(MainActivity.this);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            TextView details = new TextView(MainActivity.this);
                            details.setText(label + "\n" + (stop.name == null ? "Durak" : stop.name) + " · " + stop.lat + ", " + stop.lon);
                            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                            Button choose = new Button(MainActivity.this);
                            choose.setText("Aktar");
                            choose.setOnClickListener(view -> {
                                selectedStop[0] = index;
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(index == point.stops.size() - 1 ? "🚀 Varış Noktasını Aktar" : "🚀 Seçili Durağı Aktar");
                            });
                            row.addView(choose);
                            stopList.addView(row);
                        }
                    }
                    progress.setVisibility(View.GONE);
                    status.setText("📍 " + point.name + "\n🗺️ " + point.lat + ", " + point.lon);
                    status.setTextColor(0xff2e7d32);
                }
                @Override public void onError(String error) {
                    if (!dialog.isShowing()) return;
                    progress.setVisibility(View.GONE);
                    status.setText(error);
                    status.setTextColor(0xffc62828);
                }
            });
        };
        resolveButton.setOnClickListener(v -> resolve.run());
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            GezRouteBridge.GezPoint point = resolved[0];
            if (point == null) { resolve.run(); return; }
            GezRouteBridge.GezPoint target = selectedStop[0] >= 0 ? point.stops.get(selectedStop[0]) : point;
            if (GezRouteBridge.openInTrumore(this, target.lat, target.lon, target.name)) {
                Toast.makeText(this, "Trumore Gez açılıyor... Akıllı cihazınıza aktarılıyor.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            } else Toast.makeText(this, "Trumore açılamadı.", Toast.LENGTH_LONG).show();
        }));
        dialog.show();
        if (initial != null && !initial.trim().isEmpty()) resolve.run();
    }
    private void openAbrp() {
        Intent appIntent = getPackageManager().getLaunchIntentForPackage("com.iternio.abrpapp");
        if (appIntent == null) appIntent = getPackageManager().getLaunchIntentForPackage("com.iternio.abrp");
        try { startActivity(appIntent != null ? appIntent : new Intent(Intent.ACTION_VIEW, Uri.parse("https://abetterrouteplanner.com/"))); }
        catch (Exception ignored) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://abetterrouteplanner.com/"))); }
    }

    private View buildHomeCard(String key, VehicleTelemetry t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        card.setGravity(Gravity.CENTER);
        card.setMinimumHeight(dpToPx(115));
        card.setClickable(true);
        card.setFocusable(true);

        switch (key) {
            case CARD_BATTERY: {

                TextView socTv = new TextView(this);
                socTv.setText("%" + t.batterySocPercent);
                socTv.setTextColor(getColor(R.color.text_primary));
                socTv.setTextSize(36);
                socTv.setTypeface(null, Typeface.BOLD);
                socTv.setPadding(0, 0, 0, 0);
                socTv.setGravity(Gravity.CENTER);
                socTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(socTv);

                TextView rangeTv = new TextView(this);
                rangeTv.setText(t.estimatedRangeKm + " km menzil");
                rangeTv.setTextColor(getColor(R.color.accent_togg));
                rangeTv.setTextSize(16);
                rangeTv.setTypeface(null, Typeface.BOLD);
                rangeTv.setPadding(0, dpToPx(4), 0, 0);
                rangeTv.setGravity(Gravity.CENTER);
                rangeTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(rangeTv);

                TextView statusTv = new TextView(this);
                statusTv.setText(t.isCharging ? String.format(Locale.US, "%.1f kW", t.chargePowerKw) : t.chargeMode);
                statusTv.setTextColor(getColor(t.isCharging ? R.color.status_green : R.color.text_muted));
                statusTv.setTextSize(13);
                statusTv.setPadding(0, dpToPx(4), 0, 0);
                statusTv.setGravity(Gravity.CENTER);
                statusTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(statusTv);

                card.setOnClickListener(v -> selectTab(1));
                return card;
            }
            case CARD_12V: {

                TextView vTv = new TextView(this);
                vTv.setText(String.format(Locale.US, "%.1f V", t.lowVoltage12vVolts));
                vTv.setTextColor(getColor(R.color.text_primary));
                vTv.setTextSize(34);
                vTv.setTypeface(null, Typeface.BOLD);
                vTv.setPadding(0, 0, 0, 0);
                vTv.setGravity(Gravity.CENTER);
                vTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(vTv);

                TextView socTv = new TextView(this);
                socTv.setText("%" + t.lowVoltage12vPercent + " Kapasite");
                socTv.setTextColor(getColor(R.color.accent_togg));
                socTv.setTextSize(16);
                socTv.setTypeface(null, Typeface.BOLD);
                socTv.setPadding(0, dpToPx(4), 0, 0);
                socTv.setGravity(Gravity.CENTER);
                socTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(socTv);

                TextView statusTv = new TextView(this);
                statusTv.setText(t.is12vBatteryLow() ? "⚠️ Aracı çalıştırın" : "✓ Akü sağlıklı");
                statusTv.setTextColor(getColor(t.is12vBatteryLow() ? R.color.status_red : R.color.status_green));
                statusTv.setTextSize(13);
                statusTv.setPadding(0, dpToPx(4), 0, 0);
                statusTv.setGravity(Gravity.CENTER);
                statusTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(statusTv);

                card.setOnClickListener(v -> selectTab(2));
                return card;
            }
            case CARD_CLIMATE: {

                TextView targetTv = new TextView(this);
                targetTv.setText(String.format(Locale.US, "%.1f°C", currentTargetTemp));
                targetTv.setTextColor(getColor(R.color.text_primary));
                targetTv.setTextSize(34);
                targetTv.setTypeface(null, Typeface.BOLD);
                targetTv.setPadding(0, 0, 0, 0);
                targetTv.setGravity(Gravity.CENTER);
                targetTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(targetTv);

                TextView cabinTv = new TextView(this);
                cabinTv.setText(String.format(Locale.US, "Kabin: %.1f°C", t.cabinTempCelsius));
                cabinTv.setTextColor(getColor(R.color.accent_togg));
                cabinTv.setTextSize(16);
                cabinTv.setTypeface(null, Typeface.BOLD);
                cabinTv.setPadding(0, dpToPx(4), 0, 0);
                cabinTv.setGravity(Gravity.CENTER);
                cabinTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(cabinTv);

                TextView outdoorTv = new TextView(this);
                outdoorTv.setText(String.format(Locale.US, "Dış Ortam: %.1f°C", t.outdoorTempCelsius));
                outdoorTv.setTextColor(getColor(R.color.text_muted));
                outdoorTv.setTextSize(13);
                outdoorTv.setPadding(0, dpToPx(4), 0, 0);
                outdoorTv.setGravity(Gravity.CENTER);
                outdoorTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(outdoorTv);

                card.setOnClickListener(v -> selectTab(6));
                return card;
            }
            case CARD_DRIVE: {
                VehicleTelemetry.TripData trip = t.myTrip != null ? t.myTrip : (t.currentTrip != null ? t.currentTrip :
                        new VehicleTelemetry.TripData(t.odometerKm, 14500, 17.8, 56));
                int h = trip.durationMinutes / 60;
                int m = trip.durationMinutes % 60;
                String durStr = (h > 0) ? String.format(Locale.getDefault(), "%d sa %d dk", h, m) : String.format(Locale.getDefault(), "%d dk", m);

                TextView distTv = new TextView(this);
                distTv.setText(String.format(Locale.getDefault(), "%d km", Math.round(trip.distanceKm)));
                distTv.setTextColor(getColor(R.color.text_primary));
                distTv.setTextSize(32);
                distTv.setTypeface(null, Typeface.BOLD);
                distTv.setGravity(Gravity.CENTER);
                distTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(distTv);

                TextView durTv = new TextView(this);
                durTv.setText(durStr);
                durTv.setTextColor(getColor(R.color.accent_togg));
                durTv.setTextSize(16);
                durTv.setTypeface(null, Typeface.BOLD);
                durTv.setPadding(0, dpToPx(4), 0, 0);
                durTv.setGravity(Gravity.CENTER);
                durTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(durTv);

                TextView statsTv = new TextView(this);
                statsTv.setText(String.format(Locale.US, "%.1f kWh  •  %d km/s", trip.avgConsumption, trip.avgSpeed));
                statsTv.setTextColor(getColor(R.color.text_secondary));
                statsTv.setTextSize(13);
                statsTv.setPadding(0, dpToPx(4), 0, 0);
                statsTv.setGravity(Gravity.CENTER);
                statsTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                card.addView(statsTv);

                card.setOnClickListener(v -> selectTab(5));
                return card;
            }
            case CARD_CONTROLS: {

                Button lockBtn = new Button(this);
                lockBtn.setText(isDoorLocked ? "🔓 Kilit Aç" : "🔒 Kilitle");
                lockBtn.setTextColor(getColor(R.color.accent_togg));
                lockBtn.setBackgroundResource(R.drawable.btn_outline);
                lockBtn.setTextSize(11);
                LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(38));
                b1Lp.topMargin = dpToPx(8);
                lockBtn.setLayoutParams(b1Lp);
                lockBtn.setOnClickListener(v -> toggleDoorLock());
                card.addView(lockBtn);

                Button trunkBtn = new Button(this);
                trunkBtn.setText(isTrunkOpen ? "Bagaj Kapat" : "🚗 Bagaj Aç");
                trunkBtn.setTextColor(getColor(R.color.accent_togg));
                trunkBtn.setBackgroundResource(R.drawable.btn_outline);
                trunkBtn.setTextSize(11);
                LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(38));
                b2Lp.topMargin = dpToPx(6);
                trunkBtn.setLayoutParams(b2Lp);
                trunkBtn.setOnClickListener(v -> confirmAndToggleTrunk());
                card.addView(trunkBtn);

                return card;
            }
            case "__removed_digital_key_card": {
                LinearLayout headerRow = new LinearLayout(this);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);
                headerRow.addView(createIconHeader("🔑"));
                View spacer = new View(this);
                headerRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

                DigitalKeyManager keyMgr = new DigitalKeyManager(this);
                DigitalKey dKey = keyMgr.getActiveKey();
                boolean hasKey = dKey != null && dKey.isValid();

                headerRow.addView(createHeaderBadge(hasKey ? "Aktif" : "Anahtar", hasKey ? R.color.status_green : R.color.text_muted));
                card.addView(headerRow);

                TextView keyTitle = new TextView(this);
                keyTitle.setText(hasKey ? "Trumore Anahtar" : "Anahtar Tanımsız");
                keyTitle.setTextColor(getColor(R.color.text_primary));
                keyTitle.setTextSize(16);
                keyTitle.setTypeface(null, Typeface.BOLD);
                keyTitle.setPadding(0, dpToPx(6), 0, 0);
                card.addView(keyTitle);

                TextView keySub = new TextView(this);
                keySub.setText(hasKey ? dKey.getRemainingTimeDisplay() : "Paylaşılan linki açın");
                keySub.setTextColor(getColor(R.color.accent_purple));
                keySub.setTextSize(12);
                keySub.setPadding(0, dpToPx(2), 0, 0);
                card.addView(keySub);

                Button keyBtn = new Button(this);
                keyBtn.setText(hasKey ? "⌚ Saate Aktar" : "Anahtar Ekle");
                keyBtn.setTextColor(getColor(R.color.white));
                keyBtn.setBackgroundResource(R.drawable.btn_gradient);
                keyBtn.setTextSize(11);
                LinearLayout.LayoutParams kBp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(36));
                kBp.topMargin = dpToPx(8);
                keyBtn.setLayoutParams(kBp);
                keyBtn.setOnClickListener(v -> {
                    if (hasKey) {
                        WearSyncManager.syncDigitalKey(this, dKey);
                        Toast.makeText(this, "Dijital anahtar saate aktarılıyor... ⌚", Toast.LENGTH_SHORT).show();
                    } else {
                        showManageDigitalKeyDialog();
                    }
                });
                card.addView(keyBtn);

                Button emotorBtn = new Button(this);
                emotorBtn.setText("⚡ e-Motor (Sürüşe Hazır)");
                emotorBtn.setTextColor(getColor(R.color.white));
                emotorBtn.setBackgroundResource(R.drawable.btn_gradient);
                emotorBtn.setTextSize(11);
                LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(36));
                eLp.topMargin = dpToPx(6);
                emotorBtn.setLayoutParams(eLp);
                emotorBtn.setOnClickListener(v -> toggleReadyToDrive());
                card.addView(emotorBtn);

                card.setOnClickListener(v -> selectTab(4));
                return card;
            }
            case CARD_TIRES: {
                // Central + Axis Container
                LinearLayout crossContainer = new LinearLayout(this);
                crossContainer.setOrientation(LinearLayout.VERTICAL);
                crossContainer.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams crossLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                crossContainer.setLayoutParams(crossLp);
                crossContainer.setPadding(0, dpToPx(8), 0, dpToPx(4));
                crossContainer.setGravity(Gravity.CENTER);

                // Front Tires Row (Above '+' axis)
                LinearLayout frontRow = new LinearLayout(this);
                frontRow.setOrientation(LinearLayout.HORIZONTAL);
                frontRow.setGravity(Gravity.CENTER);

                View flBox = buildTireSubBox("Sol Ön", t.getTirePressurePsiFL(), t.tireTempFL, t.tirePressureFL < 2.2);
                View frBox = buildTireSubBox("Sağ Ön", t.getTirePressurePsiFR(), t.tireTempFR, t.tirePressureFR < 2.2);
                frontRow.addView(flBox);

                View frontGap = new View(this);
                frontGap.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(10), 1));
                frontRow.addView(frontGap);
                frontRow.addView(frBox);

                crossContainer.addView(frontRow);

                // Center '+' Axis Row
                LinearLayout axisRow = new LinearLayout(this);
                axisRow.setOrientation(LinearLayout.HORIZONTAL);
                axisRow.setGravity(Gravity.CENTER);
                axisRow.setPadding(0, dpToPx(2), 0, dpToPx(2));

                View lineLeft = new View(this);
                lineLeft.setBackgroundColor(getColor(R.color.divider));
                lineLeft.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(34), dpToPx(1.5f)));
                axisRow.addView(lineLeft);

                TextView plusTv = new TextView(this);
                plusTv.setText("+");
                plusTv.setTextColor(getColor(R.color.accent_togg));
                plusTv.setTextSize(18);
                plusTv.setTypeface(null, Typeface.BOLD);
                plusTv.setPadding(dpToPx(8), 0, dpToPx(8), 0);
                axisRow.addView(plusTv);

                View lineRight = new View(this);
                lineRight.setBackgroundColor(getColor(R.color.divider));
                lineRight.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(34), dpToPx(1.5f)));
                axisRow.addView(lineRight);

                crossContainer.addView(axisRow);

                // Rear Tires Row (Below '+' axis)
                LinearLayout rearRow = new LinearLayout(this);
                rearRow.setOrientation(LinearLayout.HORIZONTAL);
                rearRow.setGravity(Gravity.CENTER);

                View rlBox = buildTireSubBox("Sol Arka", t.getTirePressurePsiRL(), t.tireTempRL, t.tirePressureRL < 2.2);
                View rrBox = buildTireSubBox("Sağ Arka", t.getTirePressurePsiRR(), t.tireTempRR, t.tirePressureRR < 2.2);
                rearRow.addView(rlBox);

                View rearGap = new View(this);
                rearGap.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(10), 1));
                rearRow.addView(rearGap);
                rearRow.addView(rrBox);

                crossContainer.addView(rearRow);

                card.addView(crossContainer);

                card.setOnClickListener(v -> selectTab(3));
                return card;
            }
        }
        return null;
    }

    private View buildLocationCard(VehicleTelemetry t) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(14);
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView addrTv = new TextView(this);
        String addr = (tvParkAddress != null && tvParkAddress.getText() != null && !tvParkAddress.getText().toString().isEmpty())
                ? tvParkAddress.getText().toString()
                : (t.parkAddress != null ? t.parkAddress : storage.getLastAddress());
        if (addr == null || addr.isEmpty() || addr.contains("belirleniyor")) {
            addr = storage.getLastAddress();
        }
        if (addr == null || addr.isEmpty()) {
            addr = "Aracın konumu belirleniyor...";
        }
        addrTv.setText(addr);
        addrTv.setTextColor(getColor(R.color.text_primary));
        addrTv.setTextSize(14);
        addrTv.setTypeface(null, Typeface.BOLD);
        addrTv.setPadding(0, dpToPx(6), 0, dpToPx(2));
        addrTv.setGravity(Gravity.CENTER);
        addrTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(addrTv);

        TextView durTv = new TextView(this);
        durTv.setText(tvParkDuration != null ? tvParkDuration.getText() : "Park süresi hesaplanıyor...");
        durTv.setTextColor(getColor(R.color.status_green));
        durTv.setTextSize(11);
        durTv.setPadding(0, 0, 0, dpToPx(6));
        durTv.setGravity(Gravity.CENTER);
        durTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(durTv);

        // In-App Embedded Google Maps WebView
        double lat = (t.latitude != 0.0) ? t.latitude : storage.getLastLat();
        double lon = (t.longitude != 0.0) ? t.longitude : storage.getLastLon();
        if (lat == 0.0 && lon == 0.0) {
            lat = 39.925054;
            lon = 32.834755;
        }

        WebView mapWebView = new WebView(this);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(190));
        mapLp.topMargin = dpToPx(4);
        mapLp.bottomMargin = dpToPx(10);
        mapWebView.setLayoutParams(mapLp);
        mapWebView.setBackgroundResource(R.drawable.bg_map_frame);
        mapWebView.setClipToOutline(true);

        WebSettings ws = mapWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowContentAccess(true);
        ws.setLoadsImagesAutomatically(true);

        mapWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleExternalMapUrl(request.getUrl());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null) {
                    return handleExternalMapUrl(Uri.parse(url));
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                scheduleMapSnapshotCapture(mapWebView);
            }

            private boolean handleExternalMapUrl(Uri uri) {
                String u = uri.toString();
                if (!u.contains("output=embed") && !u.startsWith("data:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                        return true;
                    } catch (Exception ignored) {}
                }
                return false;
            }
        });

        mapWebView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        String mapHtml = "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>"
                + "<style>"
                + "* { margin: 0; padding: 0; box-sizing: border-box; }"
                + "html, body { width: 100%; height: 100%; overflow: hidden; background-color: #0F1724; font-family: -apple-system, sans-serif; }"
                + ".map-wrap { width: 100%; height: 100%; position: relative; }"
                + "iframe { width: 100%; height: 100%; border: none; border-radius: 12px; display: block; }"
                + ".map-loader { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #00C2D6; font-size: 13px; font-weight: bold; pointer-events: none; }"
                + "</style>"
                + "</head><body>"
                + "<div class='map-wrap'>"
                + "<div class='map-loader' id='loader'>📍 Harita Yükleniyor...</div>"
                + "<iframe src='https://maps.google.com/maps?q=" + lat + "," + lon + "&amp;hl=tr&amp;z=16&amp;output=embed' allowfullscreen onload=\"var el=document.getElementById('loader');if(el)el.style.display='none';\"></iframe>"
                + "</div></body></html>";

        mapWebView.loadDataWithBaseURL("https://maps.google.com", mapHtml, "text/html", "UTF-8", null);
        card.addView(mapWebView);
        mapWebView.postDelayed(() -> scheduleMapSnapshotCapture(mapWebView), 2800);

        Button navBtn = new Button(this);
        navBtn.setText("🧭 Haritada Aracıma Yürü / Navigasyon");
        navBtn.setTextColor(getColor(R.color.white));
        navBtn.setBackgroundResource(R.drawable.btn_gradient);
        navBtn.setTextSize(12);
        navBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40)));
        navBtn.setOnClickListener(v -> openNavigationToCar());
        card.addView(navBtn);

        return card;
    }

    private void scheduleMapSnapshotCapture(final WebView mapWebView) {
        if (mapWebView == null || isFinishing() || isDestroyed()) return;
        int width = mapWebView.getWidth();
        int height = mapWebView.getHeight();
        if (width <= 0) width = dpToPx(320);
        if (height <= 0) height = dpToPx(190);
        final int captureWidth = width;
        final int captureHeight = height;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mapWebView.isAttachedToWindow()
                && mapWebView.getWindowVisibility() == View.VISIBLE) {
            try {
                int[] location = new int[2];
                mapWebView.getLocationInWindow(location);
                android.view.Window window = getWindow();
                PixelCopy.request(window, new Rect(location[0], location[1], location[0] + captureWidth, location[1] + captureHeight), bitmap, result -> {
                    if (result == PixelCopy.SUCCESS) saveWidgetMapSnapshot(bitmap);
                    else drawAndSaveWidgetMapSnapshot(mapWebView, captureWidth, captureHeight);
                }, new Handler(Looper.getMainLooper()));
                return;
                } catch (Throwable ignored) { }
        }
        drawAndSaveWidgetMapSnapshot(mapWebView, captureWidth, captureHeight);
    }

    private void drawAndSaveWidgetMapSnapshot(WebView mapWebView, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mapWebView.draw(new Canvas(bitmap));
        saveWidgetMapSnapshot(bitmap);
    }

    private void saveWidgetMapSnapshot(Bitmap bitmap) {
        new Thread(() -> {
            File file = new File(getFilesDir(), "widget_map_snapshot.png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
                stream.flush();
                runOnUiThread(() -> ToggAppWidgetProvider.updateAllWidgets(MainActivity.this));
            } catch (Exception ignored) { }
            finally { bitmap.recycle(); }
        }, "WidgetMapSnapshot").start();
    }

    private TextView createIconHeader(String emoji) {
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(22);
        tv.setPadding(0, 0, dpToPx(4), 0);
        return tv;
    }

    private TextView createHeaderBadge(String badgeText, int colorResId) {
        TextView tv = new TextView(this);
        tv.setText(badgeText);
        tv.setTextColor(getColor(colorResId));
        tv.setTextSize(10);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackgroundResource(R.drawable.badge_pill);
        tv.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        return tv;
    }

    private View buildTireSubBox(String label, double psi, int temp, boolean isLow) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundResource(isLow ? R.drawable.tire_subcard_low : R.drawable.tire_subcard_bg);
        box.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        box.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(68), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView posTv = new TextView(this);
        posTv.setText(label);
        posTv.setSingleLine(true);
        posTv.setGravity(Gravity.CENTER);
        posTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        posTv.setTextSize(10);
        posTv.setTextColor(getColor(isLow ? R.color.status_red : R.color.text_muted));
        box.addView(posTv);

        TextView valTv = new TextView(this);
        valTv.setText(String.format(Locale.US, "%.1f", psi));
        valTv.setTextSize(15);
        valTv.setTypeface(null, Typeface.BOLD);
        valTv.setTextColor(getColor(isLow ? R.color.status_red : R.color.text_primary));
        valTv.setGravity(Gravity.CENTER);
        valTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(valTv);

        TextView tempTv = new TextView(this);
        tempTv.setText(temp + "°C");
        tempTv.setTextSize(10);
        tempTv.setTextColor(getColor(R.color.text_muted));
        tempTv.setGravity(Gravity.CENTER);
        tempTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(tempTv);

        return box;
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
            if (CARD_CONTROLS.equals(key) || CARD_CLIMATE.equals(key)) continue;
            String name = getCardDisplayName(key);
            if (name != null) {
                items.add(new CardItem(key, name, visibleSet.contains(key)));
            }
        }
        String[] allKeys = {CARD_BATTERY, CARD_DRIVE, CARD_TIRES, CARD_LOCATION, CARD_12V};
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
            case CARD_DRIVE: return "📊 Sürüş & Yolculuk";
            case CARD_TIRES: return "🛞 Lastik Basınçları (PSI)";
            case CARD_LOCATION: return "📍 Son Park Yeri & Konum";
            case CARD_12V: return "🔋 12V Alçak Gerilim Aküsü";
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
            ToggAppWidgetProvider.updateAllWidgets(this);
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
        EditText etOwnerId = dialogView.findViewById(R.id.et_owner_id);
        EditText etToken = dialogView.findViewById(R.id.et_token);
        CheckBox cbDemoMode = dialogView.findViewById(R.id.cb_demo_mode);
        CheckBox cbAbrpEnabled = dialogView.findViewById(R.id.cb_abrp_enabled);
        EditText etAbrpToken = dialogView.findViewById(R.id.et_abrp_token);
        RadioGroup abrpCapacity = dialogView.findViewById(R.id.rg_abrp_capacity);
        TextView keyStatus = dialogView.findViewById(R.id.tv_settings_key_status);
        Button manageKey = dialogView.findViewById(R.id.btn_settings_manage_key);
        Button syncKey = dialogView.findViewById(R.id.btn_settings_sync_key);
        RadioGroup themeGroup = dialogView.findViewById(R.id.rg_app_theme);
        Button customizeCards = dialogView.findViewById(R.id.btn_settings_customize_cards);
        Button abrpShortcut = dialogView.findViewById(R.id.btn_settings_abrp_shortcut);
        if (abrpShortcut != null) abrpShortcut.setOnClickListener(v -> { dialogInstance.dismiss(); showAbrpSettingsDialog(); });
        if (customizeCards != null) customizeCards.setOnClickListener(v -> { dialogInstance.dismiss(); showCustomizeHomeDialog(); });
        DigitalKey savedKey = new DigitalKeyManager(this).getActiveKey();
        if (keyStatus != null) keyStatus.setText(savedKey != null && savedKey.isValid() ? "Kayıtlı anahtar: " + savedKey.getRemainingTimeDisplay() : "Anahtar Tanımsız");
        if (manageKey != null) manageKey.setOnClickListener(v -> { dialogInstance.dismiss(); showManageDigitalKeyDialog(); });
        if (syncKey != null) syncKey.setOnClickListener(v -> {
            DigitalKey key = new DigitalKeyManager(this).getActiveKey();
            if (key != null && key.isValid()) { WearSyncManager.syncDigitalKey(this, key); Toast.makeText(this, "Dijital anahtar saate aktarılıyor.", Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(this, "Önce dijital anahtar ekleyin.", Toast.LENGTH_SHORT).show();
        });
        if (themeGroup != null) {
            String theme = storage.getAppTheme();
            themeGroup.check("light".equals(theme) ? R.id.rb_theme_light : "dark".equals(theme) ? R.id.rb_theme_dark : R.id.rb_theme_system);
            themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                storage.setAppTheme(checkedId == R.id.rb_theme_light ? "light" : checkedId == R.id.rb_theme_dark ? "dark" : "system");
                dialogInstance.dismiss(); recreate();
            });
        }

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
        if (etOwnerId != null) etOwnerId.setText(storage.getOwnerId());
        etToken.setText(storage.getAccessToken());
        cbDemoMode.setChecked(storage.isDemoMode());
        cbAbrpEnabled.setChecked(storage.isAbrpEnabled());
        etAbrpToken.setText(storage.getAbrpToken());
        abrpCapacity.check(storage.getAbrpCapacity() < 70 ? R.id.rb_abrp_standard : R.id.rb_abrp_long);
        dialogView.findViewById(R.id.btn_abrp_send).setOnClickListener(v -> {
            String token = etAbrpToken.getText().toString().trim();
            VehicleTelemetry data = currentTelemetry != null ? currentTelemetry : storage.getActiveTelemetry();
            double capacity = abrpCapacity.getCheckedRadioButtonId() == R.id.rb_abrp_standard ? 52.4 : 88.5;
            new Thread(() -> {
                boolean ok = AbrpClient.sendTelemetry(token, data, capacity);
                runOnUiThread(() -> {
                    if (ok) { storage.setAbrpLastSync(System.currentTimeMillis()); Toast.makeText(this, "ABRP telemetrisi gönderildi.", Toast.LENGTH_SHORT).show(); }
                    else Toast.makeText(this, "ABRP gönderimi başarısız. Token ve bağlantıyı kontrol edin.", Toast.LENGTH_LONG).show();
                });
            }, "abrp-manual-send").start();
        });
        dialogView.findViewById(R.id.btn_abrp_open).setOnClickListener(v -> {
            Intent appIntent = getPackageManager().getLaunchIntentForPackage("com.iternio.abrpapp");
            if (appIntent == null) appIntent = getPackageManager().getLaunchIntentForPackage("com.iternio.abrp");
            try { startActivity(appIntent != null ? appIntent : new Intent(Intent.ACTION_VIEW, Uri.parse("https://abetterrouteplanner.com/"))); }
            catch (Exception ignored) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://abetterrouteplanner.com/"))); }
        });

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
            if (etOwnerId != null) storage.setOwnerId(etOwnerId.getText().toString());
            if (!enteredToken.isEmpty()) {
                storage.setAccessToken(enteredToken);
            }
            storage.setAbrpToken(etAbrpToken.getText().toString());
            storage.setAbrpEnabled(cbAbrpEnabled.isChecked());
            storage.setAbrpCapacity(abrpCapacity.getCheckedRadioButtonId() == R.id.rb_abrp_standard ? 52.4 : 88.5);
            configureAbrpJob(storage.isAbrpEnabled());
            storage.setDemoMode(cbDemoMode.isChecked() || !storage.hasValidToken());
            if (!storage.isDemoMode() && bleManager.isDemoConnected()) bleManager.disconnect();
            ToggAppWidgetProvider.updateAllWidgets(this);
            refreshTelemetry();
        });

        dialogInstance.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.btn_cancel), (dialog, which) -> dialog.dismiss());
        dialogInstance.show();
    }

    private void maybeSendAbrp(VehicleTelemetry telemetry) {
        if (!storage.isAbrpEnabled() || telemetry == null || System.currentTimeMillis() - storage.getAbrpLastSync() < AbrpJobService.INTERVAL_MILLIS) return;
        new Thread(() -> {
            if (AbrpClient.sendTelemetry(storage.getAbrpToken(), telemetry, storage.getAbrpCapacity())) storage.setAbrpLastSync(System.currentTimeMillis());
        }, "abrp-refresh-send").start();
    }

    private void showAbrpSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(4));

        CheckBox enabled = new CheckBox(this);
        enabled.setText("7 dakikada bir ABRP'ye gönder");
        enabled.setChecked(storage.isAbrpEnabled());
        layout.addView(enabled);

        EditText token = new EditText(this);
        token.setHint("ABRP Generic Token");
        token.setSingleLine(true);
        token.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(storage.getAbrpToken());
        layout.addView(token);

        RadioGroup capacity = new RadioGroup(this);
        RadioButton longRange = new RadioButton(this);
        longRange.setId(View.generateViewId());
        longRange.setText("88.5 kWh (Uzun Menzil)");
        RadioButton standard = new RadioButton(this);
        standard.setId(View.generateViewId());
        standard.setText("52.4 kWh (Standart)");
        capacity.addView(longRange);
        capacity.addView(standard);
        capacity.check(storage.getAbrpCapacity() < 70 ? standard.getId() : longRange.getId());
        layout.addView(capacity);

        Button send = new Button(this);
        send.setText("Şimdi Gönder / Test Et");
        send.setOnClickListener(v -> sendAbrpTelemetry(currentTelemetry != null ? currentTelemetry : storage.getActiveTelemetry(), token.getText().toString().trim(), capacity.getCheckedRadioButtonId() == standard.getId() ? 52.4 : 88.5));
        layout.addView(send);
        Button open = new Button(this);
        open.setText("ABRP'yi Aç");
        open.setOnClickListener(v -> openAbrp());
        layout.addView(open);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("ABRP Canlı Telemetri")
                .setView(layout)
                .setPositiveButton(getString(R.string.btn_save), null)
                .setNegativeButton(getString(R.string.btn_cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            storage.setAbrpToken(token.getText().toString().trim());
            storage.setAbrpEnabled(enabled.isChecked());
            storage.setAbrpCapacity(capacity.getCheckedRadioButtonId() == standard.getId() ? 52.4 : 88.5);
            configureAbrpJob(enabled.isChecked());
            renderHomeCards();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void sendAbrpTelemetry(VehicleTelemetry data, String token, double capacity) {
        if (token == null || token.trim().isEmpty() || data == null) {
            Toast.makeText(this, "ABRP token veya araç telemetrisi eksik.", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            boolean ok = AbrpClient.sendTelemetry(token, data, capacity);
            runOnUiThread(() -> {
                if (ok) { storage.setAbrpLastSync(System.currentTimeMillis()); Toast.makeText(this, "ABRP telemetrisi gönderildi.", Toast.LENGTH_SHORT).show(); }
                else Toast.makeText(this, "ABRP gönderimi başarısız. Token ve bağlantıyı kontrol edin.", Toast.LENGTH_LONG).show();
            });
        }, "abrp-manual-send").start();
    }

    private void configureAbrpJob(boolean enabled) {
        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (enabled) { AbrpJobService.scheduleNext(this); }
        else { scheduler.cancel(AbrpJobService.JOB_ID); }
    }

    private void applyThemeBeforeContent() {
        String theme = storage.getAppTheme();
        if ("light".equals(theme)) setTheme(R.style.Theme_ToggCompanion_Light);
        else if ("dark".equals(theme)) setTheme(R.style.Theme_ToggCompanion_Dark);
        else {
            int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            setTheme(night == android.content.res.Configuration.UI_MODE_NIGHT_YES ? R.style.Theme_ToggCompanion_Dark : R.style.Theme_ToggCompanion_Light);
        }
    }

    private String formatSyncTime(long millis) {
        if (millis <= 0) return "--:--";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    private String formatTrumoreSyncTime(long millis) {
        if (millis <= 0) return "--:--";
        return new SimpleDateFormat("d MMMM yyyy - HH:mm", new Locale("tr", "TR")).format(new Date(millis));
    }

    private void showEditVehicleNameDialog() {
        if (isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ Araç İsmini Düzenle");
        builder.setMessage("Ana sayfada görüntülenecek araç model veya isminizi giriniz:");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8));

        final EditText etName = new EditText(this);
        etName.setHint("Örn: Togg T10F Fastback");
        etName.setText(storage.getVehicleName());
        etName.setSelection(etName.getText().length());
        etName.setSingleLine(true);
        etName.setTextSize(16);
        layout.addView(etName);

        builder.setView(layout);
        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) newName = "Togg T10F Fastback";
            storage.setVehicleName(newName);
            ToggAppWidgetProvider.updateAllWidgets(this);
            if (tvVehicleTitle != null) tvVehicleTitle.setText(newName);
            Toast.makeText(this, "Araç ismi güncellendi: " + newName, Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton("Varsayılana Sıfırla", (dialog, which) -> {
            storage.setVehicleName("Togg T10F Fastback");
            ToggAppWidgetProvider.updateAllWidgets(this);
            if (tvVehicleTitle != null) tvVehicleTitle.setText("Togg T10F Fastback");
            Toast.makeText(this, "Araç ismi varsayılana sıfırlandı", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        ToggAppWidgetProvider.updateAllWidgets(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bleManager.removeListener(bleListener);
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
