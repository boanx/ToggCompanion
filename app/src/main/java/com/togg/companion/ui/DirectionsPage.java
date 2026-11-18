package com.togg.companion.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import com.togg.companion.R;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.service.AbrpSyncEngine;
import com.togg.companion.service.AbrpSyncService;
import java.text.DateFormat;
import java.util.Date;

/** Embedded eighth dashboard page. Route transfer and ABRP setup live together here. */
final class DirectionsPage {
    private final Activity activity;
    private final SecureTokenStorage storage;
    private TextView state, last;
    private Switch enabled;
    private boolean updating;
    private final Handler handler = new Handler(Looper.getMainLooper());
    DirectionsPage(Activity activity, SecureTokenStorage storage) { this.activity = activity; this.storage = storage; }
    View create(Runnable sendRoute) {
        LinearLayout root = column();
        TextView title = label("Yol Tarifi"); title.setTextSize(22); root.addView(title);
        Button route = button("Rotayı araca aktar"); route.setOnClickListener(v -> sendRoute.run()); root.addView(route);
        TextView abrp = label("ABRP entegrasyonu"); abrp.setTextColor(activity.getColor(R.color.accent_togg)); root.addView(abrp);
        LinearLayout tabs = new LinearLayout(activity);
        Button syncTab = button("Senkronizasyon"), settingsTab = button("Ayarlar");
        tabs.addView(syncTab, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(settingsTab, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(tabs);
        View sync = overview(), settings = settings(); root.addView(sync); root.addView(settings);
        java.util.function.Consumer<Boolean> select = showSettings -> {
            sync.setVisibility(showSettings ? View.GONE : View.VISIBLE);
            settings.setVisibility(showSettings ? View.VISIBLE : View.GONE);
            syncTab.setBackgroundResource(showSettings ? R.drawable.tab_unselected_bg : R.drawable.tab_selected_bg);
            settingsTab.setBackgroundResource(showSettings ? R.drawable.tab_selected_bg : R.drawable.tab_unselected_bg);
        };
        syncTab.setOnClickListener(v -> select.accept(false)); settingsTab.setOnClickListener(v -> select.accept(true)); select.accept(false);
        return root;
    }
    void close() { handler.removeCallbacksAndMessages(null); }
    private View overview() {
        LinearLayout layout = column();
        state = label(""); last = label(""); layout.addView(state); layout.addView(last);
        enabled = new Switch(activity); enabled.setText("Güncel araç verilerini gönder"); layout.addView(enabled);
        layout.addView(label("Batarya ve Yolculuğum tüketimi her 1 dakikada bir araç servisinden alınarak ABRP'ye gönderilir. Aktarım uygulama arka plandayken de sürer."));
        layout.addView(label("Android aktarımı durdurursa buradan yeniden açabilirsiniz. Aktarım sırasında pil ve internet kullanılır."));
        enabled.setOnCheckedChangeListener((view, checked) -> {
            if (updating) return;
            if (checked && !canStart()) { message("Önce Togg ID ile giriş yapın ve Ayarlar sekmesinden ABRP bağlantısını kaydedin."); render(); return; }
            storage.setAbrpEnabled(checked);
            if (checked) {
                if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 302);
                if (!AbrpSyncService.start(activity)) message("Arka plan aktarımı başlatılamadı. Yeniden deneyin.");
            } else AbrpSyncService.stop(activity);
            handler.postDelayed(this::render, 300);
        });
        Button send = button("Güncel veriyi şimdi gönder"); layout.addView(send);
        send.setOnClickListener(v -> {
            if (!canStart()) { message("Önce araç ve ABRP bağlantısını tamamlayın."); return; }
            send.setEnabled(false);
            AbrpSyncEngine.send(activity, () -> !activity.isDestroyed(), ok -> activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) return;
                send.setEnabled(true); render(); message(ok ? "Güncel veriler gönderildi." : "Veriler gönderilemedi. Bağlantıyı kontrol edin.");
            }));
        });
        Button open = button("ABRP'yi aç"); layout.addView(open);
        open.setOnClickListener(v -> {
            Intent app = activity.getPackageManager().getLaunchIntentForPackage("com.iternio.abrpapp");
            if (app == null) app = activity.getPackageManager().getLaunchIntentForPackage("com.iternio.abrp");
            try { activity.startActivity(app != null ? app : new Intent(Intent.ACTION_VIEW, Uri.parse("https://abetterrouteplanner.com/"))); }
            catch (Exception error) { message("ABRP açılamadı. Uygulamayı veya bir tarayıcıyı yükleyin."); }
        });
        render(); return layout;
    }
    private View settings() {
        LinearLayout layout = column(); layout.addView(label("ABRP bağlantı anahtarı"));
        EditText token = new EditText(activity); token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("ABRP Generic Token"); token.setText(storage.getAbrpToken()); layout.addView(token);
        layout.addView(label("Batarya kapasitesi"));
        RadioGroup capacity = new RadioGroup(activity);
        RadioButton longRange = new RadioButton(activity); longRange.setId(View.generateViewId()); longRange.setText("88,5 kWh · Uzun menzil"); capacity.addView(longRange);
        RadioButton standard = new RadioButton(activity); standard.setId(View.generateViewId()); standard.setText("52,4 kWh · Standart menzil"); capacity.addView(standard);
        capacity.check(storage.getAbrpCapacity() < 70 ? standard.getId() : longRange.getId()); layout.addView(capacity);
        Button save = button("Kaydet"); layout.addView(save);
        save.setOnClickListener(v -> {
            String value = token.getText().toString().trim();
            if (value.isEmpty()) { message("ABRP bağlantı anahtarını girin."); return; }
            storage.setAbrpToken(value);
            storage.setAbrpCapacity(capacity.getCheckedRadioButtonId() == standard.getId() ? 52.4 : 88.5);
            com.togg.companion.widget.ToggAppWidgetProvider.updateAllWidgets(activity);
            message("ABRP ayarları kaydedildi.");
        });
        return layout;
    }
    private boolean canStart() { return !storage.getAbrpToken().isEmpty() && storage.hasValidToken() && !storage.isDemoMode() && !storage.isDemoVin(); }
    void render() {
        if (state == null || last == null || enabled == null) return;
        boolean active = storage.isAbrpEnabled() && AbrpSyncService.isRunning();
        state.setText("Veri aktarımı: " + (active ? "Aktif" : "Deaktif"));
        long time = storage.getAbrpLastSync();
        last.setText("Son veri gönderimi: " + (time > 0 ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(time)) : "Henüz gönderilmedi"));
        updating = true; enabled.setChecked(storage.isAbrpEnabled()); updating = false;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); int p = Math.round(16 * activity.getResources().getDisplayMetrics().density); v.setPadding(p,p,p,p); return v; }
    private TextView label(String text) { TextView v = new TextView(activity); v.setText(text); v.setTextSize(15); v.setPadding(0,12,0,16); return v; }
    private Button button(String text) { Button v = new Button(activity); v.setText(text); return v; }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
