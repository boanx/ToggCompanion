package com.togg.companion.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.togg.companion.R;
import com.togg.companion.core.GezRouteBridge;
import com.togg.companion.data.SecureTokenStorage;

import java.util.Locale;

/** Manual PlannedJourneys submission. No external app is opened by this screen. */
public final class VehicleRouteDialog {
    private final Activity activity;
    private final SecureTokenStorage storage;
    private final LinearLayout content;
    private final EditText input;
    private final TextView status;
    private final TextView preview;
    private final RadioGroup targets;
    private final ProgressBar progress;
    private final Button resolveButton;
    private final AlertDialog dialog;
    private GezRouteBridge.GezPoint resolved;
    private GezRouteBridge.GezPoint selected;
    private int generation;
    private boolean resolving;
    private boolean sending;
    private boolean accepted;

    public static AlertDialog show(Activity activity, SecureTokenStorage storage, String prefill) {
        return new VehicleRouteDialog(activity, storage, prefill).open();
    }

    private VehicleRouteDialog(Activity activity, SecureTokenStorage storage, String prefill) {
        this.activity = activity;
        this.storage = storage;
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(16));

        TextView explanation = text("Harita bağlantısı, adres veya koordinat girin. Hedefi kontrol edip doğrudan aracınıza gönderin.", 14);
        add(explanation);
        String vehicle = storage.getPlate();
        TextView account = text("Companion aracı: " + (vehicle == null || vehicle.trim().isEmpty() ? storage.getVin() : vehicle)
                + "\n" + (canSend() ? "Togg ID bağlı" : "Göndermek için gerçek araç bilgisi ve Togg ID oturumu gerekli"), 12);
        add(account);

        input = new EditText(activity);
        input.setHint("Harita bağlantısı / adres / enlem, boylam");
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (prefill != null && !prefill.trim().isEmpty()) input.setText(prefill.trim());
        add(input);

        status = text("Önce hedefi önizleyin.", 13);
        Button paste = button("Panodan yapıştır");
        paste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) { status.setText("Pano boş."); return; }
            CharSequence value = clip.getItemAt(0).coerceToText(activity);
            if (value != null) input.setText(value);
        });
        add(paste);
        resolveButton = button("Hedefi önizle");
        resolveButton.setOnClickListener(v -> resolve());
        add(resolveButton);

        progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        content.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
        status.setTextIsSelectable(true);
        add(status);
        preview = text("", 15);
        preview.setTypeface(null, Typeface.BOLD);
        add(preview);
        targets = new RadioGroup(activity);
        targets.setOrientation(RadioGroup.VERTICAL);
        add(targets);

        TextView note = text("Gönderim internet üzerinden Togg ID hesabınızın araç eşlemesini kullanır; BLE bağlantısı gerekmez. Bulut kabulü sonrası hedefin araç ekranında görünmesini kontrol edin.", 12);
        add(note);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Akıllı cihazıma aktar")
                .setView(scroll)
                .setNegativeButton("Kapat", null)
                .setPositiveButton("Akıllı cihazıma aktar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> send());
            updateSendButton();
            if (input.getText().length() > 0) resolve();
        });
        dialog.setOnDismissListener(ignored -> generation++);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                generation++;
                resolved = null; selected = null; accepted = false; resolving = false;
                targets.removeAllViews(); preview.setText(""); progress.setVisibility(View.GONE);
                status.setText("Hedef değişti. Yeniden önizleyin.");
                resolveButton.setEnabled(true);
                updateSendButton();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private AlertDialog open() { dialog.show(); return dialog; }

    private void resolve() {
        if (resolving || sending) return;
        String source = input.getText().toString().trim();
        if (source.isEmpty()) { status.setText("Bir hedef girin."); return; }
        final int requestGeneration = ++generation;
        resolved = null; selected = null; accepted = false;
        targets.removeAllViews(); preview.setText(""); resolving = true;
        resolveButton.setEnabled(false); progress.setVisibility(View.VISIBLE);
        status.setText("Hedef çözümleniyor…"); updateSendButton();
        // Geocoder needs only an application context; do not retain an Activity in worker code.
        GezRouteBridge.resolveAsync(activity.getApplicationContext(), source, new GezRouteBridge.Callback() {
            @Override public void onResolved(GezRouteBridge.GezPoint point) {
                if (!active() || requestGeneration != generation) return;
                resolving = false; resolveButton.setEnabled(true); progress.setVisibility(View.GONE);
                resolved = point; selected = point;
                showPreview();
                status.setText(canSend() ? "Hedef hazır. Göndermeden önce adı ve koordinatları kontrol edin."
                        : "Hedef hazır. Göndermek için Ayarlar’dan Togg ID ile giriş yapın ve gerçek VIN’i seçin.");
                updateSendButton();
            }
            @Override public void onError(String error) {
                if (!active() || requestGeneration != generation) return;
                resolving = false; resolveButton.setEnabled(true); progress.setVisibility(View.GONE);
                status.setText(error); updateSendButton();
            }
        });
    }

    private void showPreview() {
        preview.setText(resolved.hasMultipleStops() ? resolved.stops.size() + " noktalı rota" : describe(resolved));
        if (!resolved.hasMultipleStops()) return;
        RadioButton whole = choice("Tüm rota · başlangıç, ara duraklar ve varış");
        targets.addView(whole);
        whole.setChecked(true);
        whole.setOnClickListener(v -> selectTarget(resolved));
        for (int i = 0; i < resolved.stops.size(); i++) {
            GezRouteBridge.GezPoint point = resolved.stops.get(i);
            String role = i == 0 ? "Başlangıç" : i == resolved.stops.size() - 1 ? "Varış" : "Ara durak " + i;
            RadioButton stop = choice(role + "\n" + describe(point) + "\nYalnız bu noktayı hedef olarak gönder");
            targets.addView(stop);
            stop.setOnClickListener(v -> selectTarget(point));
        }
    }

    private void selectTarget(GezRouteBridge.GezPoint point) {
        selected = point;
        accepted = false;
        status.setText(point.hasMultipleStops() ? "Tüm rota seçildi. Başlangıç, ara duraklar ve varış gönderilecek."
                : "Yalnız bu hedef gönderilecek:\n" + describe(point));
        updateSendButton();
    }

    private void send() {
        if (sending || resolving || selected == null || accepted) return;
        if (!canSend()) { status.setText("Togg ID oturumu ve gerçek VIN gerekli."); return; }
        final GezRouteBridge.GezPoint route = selected;
        sending = true;
        setEditingEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("Hedef gönderiliyor…");
        updateSendButton();
        dialog.setCancelable(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        GezRouteBridge.dispatchToVehicleCloud(activity.getApplicationContext(), storage, route,
                new GezRouteBridge.CloudCallback() {
                    @Override public void onSuccess(String message) {
                        activity.runOnUiThread(() -> complete(true, message));
                    }
                    @Override public void onError(String message) {
                        activity.runOnUiThread(() -> complete(false, message));
                    }
                });
    }

    private void complete(boolean success, String message) {
        sending = false;
        if (!active()) return;
        accepted = success;
        progress.setVisibility(View.GONE);
        setEditingEnabled(true);
        dialog.setCancelable(true);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
        status.setText(message + (success ? "\nAraç ekranındaki hedefi kontrol edin. Yeni hedef girebilir veya bu pencereyi kapatabilirsiniz." : "\nHedefiniz korundu. Sorunu giderip yeniden deneyebilirsiniz."));
        updateSendButton();
    }

    private boolean active() { return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed(); }
    private boolean canSend() { return storage.hasValidToken() && !storage.isDemoMode() && !storage.isDemoVin(); }
    private void updateSendButton() {
        if (!dialog.isShowing()) return;
        Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        send.setEnabled(!sending && !resolving && !accepted && selected != null && canSend());
        send.setText(sending ? "Gönderiliyor…" : accepted ? "Bulut kabul etti" : "Akıllı cihazıma aktar");
    }
    private void setEditingEnabled(boolean enabled) {
        input.setEnabled(enabled); resolveButton.setEnabled(enabled);
        for (int i = 0; i < content.getChildCount(); i++) {
            View view = content.getChildAt(i);
            if (view instanceof Button) view.setEnabled(enabled);
        }
        for (int i = 0; i < targets.getChildCount(); i++) targets.getChildAt(i).setEnabled(enabled);
    }
    private RadioButton choice(String label) {
        RadioButton button = new RadioButton(activity);
        button.setId(View.generateViewId()); button.setText(label); button.setTextSize(13);
        button.setTextColor(activity.getColor(R.color.text_primary)); button.setMinHeight(dp(48));
        button.setPadding(dp(4), dp(8), dp(4), dp(8));
        return button;
    }
    private String describe(GezRouteBridge.GezPoint point) {
        return (point.name == null || point.name.trim().isEmpty() ? "Hedef" : point.name.trim())
                + String.format(Locale.US, "\n%.6f, %.6f", point.lat, point.lon);
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size);
        view.setTextColor(activity.getColor(R.color.text_secondary)); return view;
    }
    private Button button(String title) { Button b = new Button(activity); b.setText(title); b.setMinHeight(dp(48)); return b; }
    private void add(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12); content.addView(view, lp);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
