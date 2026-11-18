package com.togg.companion.ui;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import com.togg.companion.core.OdometerExport;
import com.togg.companion.data.OdometerExportStore;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.service.OdometerExportJobService;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/** Google Drive can be selected in Android's installed document providers. No broad Drive access. */
public final class OdometerExportActivity extends Activity {
    private static final int CREATE_JSON = 4201;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private OdometerExportStore config;
    private Switch enabled;
    private EditText interval;
    private TextView status, destination;
    private Button date, time;
    private long first;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { renderStatus(); handler.postDelayed(this, 2000); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        config = new OdometerExportStore(this);
        first = saved != null ? saved.getLong("first") : config.first();
        if (first <= 0) { Calendar c = Calendar.getInstance(); c.add(Calendar.MINUTE, 15); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); first = c.getTimeInMillis(); }
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        scroll.addView(root); setContentView(scroll);
        Button back = button(root, "Geri"); back.setOnClickListener(v -> finish());
        label(root, "Kilometre paylaşımı").setTextSize(24);
        label(root, "Seçtiğiniz JSON dosyası toplam kilometre, araç veri zamanı ve sorgulama zamanı ile güncellenir.");
        destination = label(root, "");
        button(root, "Google Drive'da JSON dosyası oluştur").setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "togg-kilometre.json")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(intent, CREATE_JSON); }
            catch (Exception error) { message("Dosya seçici açılamadı."); }
        });
        label(root, "Açılan dosya seçicide Google Drive hesabınızı seçin. Drive görünmüyorsa Drive uygulamasını yükleyip giriş yapın.");
        enabled = new Switch(this); enabled.setText("Otomatik gönderim");
        enabled.setChecked(saved != null ? saved.getBoolean("enabled") : config.enabled()); root.addView(enabled);
        label(root, "İlk gönderim tarihi ve saati");
        date = button(root, ""); time = button(root, "");
        date.setOnClickListener(v -> {
            Calendar c = calendar();
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar next = calendar(); next.set(year, month, day); first = next.getTimeInMillis(); renderTime();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        time.setOnClickListener(v -> {
            Calendar c = calendar();
            new TimePickerDialog(this, (view, hour, minute) -> {
                Calendar next = calendar(); next.set(Calendar.HOUR_OF_DAY, hour); next.set(Calendar.MINUTE, minute);
                next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0); first = next.getTimeInMillis(); renderTime();
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });
        label(root, "Tekrar aralığı (dakika, en az 15)");
        interval = new EditText(this); interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText(saved != null ? saved.getString("minutes") : String.valueOf(config.minutes())); root.addView(interval);
        label(root, "Örnek: 60 = saatte bir, 1440 = günde bir. Telefonun pil ve internet durumu gönderimi geciktirebilir. Kaçırılan gönderimler topluca tekrarlanmaz.");
        button(root, "Ayarları kaydet").setOnClickListener(v -> { if (save(false)) message("Gönderim ayarları kaydedildi."); });
        button(root, "Şimdi gönder").setOnClickListener(v -> {
            if (save(true)) message(OdometerExportJobService.sendNow(this)
                    ? "Gönderim sıraya alındı; sonucu aşağıdan takip edebilirsiniz." : "Gönderim zaten bekliyor veya başlatılamadı.");
        });
        status = label(root, "");
        label(root, "Araç buluttaki son ölçümünü döndürebilir. JSON içindeki vehicle_data_at araç verisinin zamanıdır. Dosyaya yazılması, Drive bulut eşitlemesinin tamamlandığını doğrulamaz.");
        renderTime(); renderStatus();
    }
    private boolean save(boolean manual) {
        try {
            long minutes = Long.parseLong(interval.getText().toString().trim());
            OdometerExport.nextTime(first, minutes, System.currentTimeMillis());
            if (config.uri().isEmpty()) { message("Önce Google Drive'da JSON dosyasını oluşturun."); return false; }
            SecureTokenStorage auth = new SecureTokenStorage(this);
            boolean ready = auth.hasValidToken() && !auth.isDemoMode() && !auth.isDemoVin();
            if ((manual || enabled.isChecked()) && !ready) { message("Önce gerçek aracınız için Togg ID ile giriş yapın."); return false; }
            String scope = ready ? OdometerExportStore.accountScope(auth) : config.scope();
            if ((manual || enabled.isChecked()) && scope.isEmpty()) { message("Togg ID bağlantısını yenileyin."); return false; }
            config.save(enabled.isChecked(), first, minutes, scope);
            if (!OdometerExportJobService.schedule(this)) {
                config.save(false, first, minutes, scope); enabled.setChecked(false);
                message("Otomatik gönderim planlanamadı. Yeniden deneyin."); renderStatus(); return false;
            }
            renderStatus(); return true;
        } catch (Exception error) { message("Tekrar aralığı 15 ile 525600 dakika arasında olmalı."); return false; }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CREATE_JSON || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) throw new SecurityException();
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String previous = config.uri();
            config.destination(uri.toString()); enabled.setChecked(false); OdometerExportJobService.schedule(this);
            if (!previous.isEmpty() && !previous.equals(uri.toString())) {
                for (android.content.UriPermission grant : getContentResolver().getPersistedUriPermissions()) {
                    if (grant.getUri().toString().equals(previous)) {
                        int oldFlags = (grant.isReadPermission() ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0)
                                | (grant.isWritePermission() ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
                        try { getContentResolver().releasePersistableUriPermission(grant.getUri(), oldFlags); } catch (Exception ignored) { }
                    }
                }
            }
            renderStatus(); message("Dosya seçildi. Ayarları kaydedin veya Şimdi gönder'e basın.");
        } catch (Exception error) { message("Bu dosyaya kalıcı yazma izni alınamadı. Başka bir konum seçin."); }
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("first", first); state.putBoolean("enabled", enabled.isChecked());
        state.putString("minutes", interval.getText().toString()); super.onSaveInstanceState(state);
    }
    private void renderStatus() {
        if (status == null) return;
        destination.setText(config.uri().isEmpty() ? "Dosya seçilmedi" : "JSON dosyası seçildi");
        String next = config.enabled() && config.next() > 0 ? DateFormat.getDateTimeInstance().format(new Date(config.next()))
                + (config.next() <= System.currentTimeMillis() ? " (çalışması bekleniyor)" : "") : "Kapalı";
        status.setText("Otomatik gönderim: " + (config.enabled() ? "Açık" : "Kapalı")
                + "\nSon dosya güncellemesi: " + (config.last() > 0 ? DateFormat.getDateTimeInstance().format(new Date(config.last())) : "Yok")
                + "\nSonuç: " + config.status() + "\nSonraki planlanan zaman: " + next);
    }
    private Calendar calendar() { Calendar c = Calendar.getInstance(); c.setTimeInMillis(first); return c; }
    private void renderTime() { date.setText(DateFormat.getDateInstance().format(new Date(first))); time.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(first))); }
    private TextView label(LinearLayout root, String value) { TextView v = new TextView(this); v.setText(value); v.setTextSize(15); v.setPadding(0,16,0,16); root.addView(v); return v; }
    private Button button(LinearLayout root, String value) { Button v = new Button(this); v.setText(value); root.addView(v); return v; }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
