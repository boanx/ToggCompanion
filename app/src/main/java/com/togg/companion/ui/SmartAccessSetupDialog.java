package com.togg.companion.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.togg.companion.core.VehicleBleManager;
import com.togg.companion.core.AppLog;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.SmartAccessAccountStorage;
import com.togg.companion.data.remote.SmartAccessInvitationClient;

import java.util.Locale;

/** Normal account provisioning UI; invitation metadata cannot activate access. */
final class SmartAccessSetupDialog {
    private SmartAccessSetupDialog() { }

    static AlertDialog show(Activity activity, SecureTokenStorage storage, VehicleBleManager manager,
                            Runnable onStatusChanged) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);

        TextView explanation = new TextView(activity);
        explanation.setText("Araç bilgilerinizi doğrulayın ve dijital anahtarınızı etkinleştirin. Paylaşılan anahtar için daveti kabul edip araç sahibinin onayını tamamlayın.");
        content.addView(explanation);

        TextView vinLabel = new TextView(activity);
        vinLabel.setText("Araç şasi numarası (VIN)");
        content.addView(vinLabel);
        EditText vinInput = new EditText(activity);
        vinInput.setSingleLine(true);
        vinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        vinInput.setText(storage.isDemoVin() ? "" : storage.getVin());
        vinInput.setHint("17 haneli gerçek araç VIN'i");
        content.addView(vinInput);

        TextView identityLabel = new TextView(activity);
        identityLabel.setText("Trumore kullanıcı kimliği (truId)");
        content.addView(identityLabel);
        EditText identityInput = new EditText(activity);
        identityInput.setSingleLine(true);
        identityInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        String configured = manager.getConfiguredTruId();
        identityInput.setText(configured == null || configured.trim().isEmpty()
                ? manager.getSuggestedTruId() : configured);
        identityInput.setHint("Togg ID hesabındaki kullanıcı kimliği");
        content.addView(identityInput);
        identityLabel.setVisibility(android.view.View.GONE);
        identityInput.setVisibility(android.view.View.GONE);

        CheckBox owner = new CheckBox(activity);
        owner.setText("Aracın sahibiyim");
        owner.setChecked(storage.getOwnerId().isEmpty());
        content.addView(owner);
        TextView ownerNote = new TextView(activity);
        ownerNote.setText("Paylaşılan araçta bu seçeneği kapatın. Erişim yetkisini sunucu doğrular.");
        content.addView(ownerNote);

        TextView status = new TextView(activity);
        status.setPadding(0, padding / 2, 0, padding / 2);
        status.setText(AppLog.userMessage(activity, "BLE", manager.getAccessStatusText(), "Dijital anahtar kurulumu veya bağlantı bekleniyor."));
        content.addView(status);

        Button fetch = new Button(activity);
        fetch.setText("Dijital anahtarı etkinleştir");
        content.addView(fetch);
        Button authenticate = new Button(activity);
        authenticate.setText("Kayıtlı anahtarla oturumu aç");
        content.addView(authenticate);
        Button create = new Button(activity);
        create.setText("Yeni dijital anahtar oluştur");
        content.addView(create);
        Button login = new Button(activity);
        login.setText("Togg ID ile giriş yap");
        content.addView(login);

        TextView guestNote = new TextView(activity);
        SmartAccessAccountStorage guestStorage = new SmartAccessAccountStorage(activity);
        guestNote.setText(guestStorage.hasValidToken()
                ? "Ayrı paylaşılan anahtar hesabı kayıtlı. Daveti inceleyip kabul edin, ardından paylaşılan anahtarı etkinleştirin."
                : "Mevcut Trumore anahtarını korumak için ayrı paylaşılan anahtar hesabıyla giriş yapın; ardından daveti inceleyin.");
        content.addView(guestNote);
        Button guestLogin = new Button(activity);
        guestLogin.setText("Paylaşılan anahtar hesabıyla giriş yap");
        guestLogin.setEnabled(guestStorage.isAvailable());
        content.addView(guestLogin);
        Button invitationPreview = new Button(activity);
        invitationPreview.setText("Paylaşılan anahtar davetini incele");
        invitationPreview.setEnabled(guestStorage.isAvailable());
        content.addView(invitationPreview);
        Button acceptInvitation = new Button(activity);
        acceptInvitation.setText("İncelenen daveti kabul et");
        acceptInvitation.setEnabled(false);
        content.addView(acceptInvitation);
        Button guestCertificate = new Button(activity);
        guestCertificate.setText("Paylaşılan anahtarı etkinleştir");
        guestCertificate.setEnabled(guestStorage.isAvailable());
        content.addView(guestCertificate);
        String[] selectedInvitation = {null};

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Smart Access kurulumu")
                .setView(scroll)
                .setNegativeButton("Kapat", null)
                .create();
        VehicleBleManager.OnAccessStateChangeListener listener = state -> activity.runOnUiThread(() -> {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            status.setText(AppLog.userMessage(activity, "BLE", manager.getAccessStatusText(), "Dijital anahtar kurulumu veya bağlantı bekleniyor."));
            onStatusChanged.run();
        });
        VehicleBleManager.AccessCallback callback = (success, message) -> activity.runOnUiThread(() -> {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            fetch.setEnabled(true);
            authenticate.setEnabled(true);
            create.setEnabled(true);
            String current = manager.getAccessStatusText();
            status.setText(AppLog.userMessage(activity, "BLE", message, success ? "Dijital anahtar hazır." : "Dijital anahtar yetkilendirilemedi. Hesabı ve araç onayını kontrol edin."));
            onStatusChanged.run();
        });
        Runnable markPending = () -> {
            fetch.setEnabled(false);
            authenticate.setEnabled(false);
            create.setEnabled(false);
            status.setText("İşlem sürüyor...");
        };
        fetch.setOnClickListener(view -> {
            if (!owner.isChecked() && guestStorage.hasAccount()) {
                guestCertificate.performClick();
                return;
            }
            if (!saveInputs(storage, manager, vinInput, identityInput)) return;
            markPending.run();
            manager.prepareSmartAccess(identityInput.getText().toString().trim(), owner.isChecked(), callback);
        });
        authenticate.setOnClickListener(view -> {
            String configuredIdentity = manager.getConfiguredTruId();
            if (configuredIdentity == null || !identityInput.getText().toString().trim().equals(configuredIdentity)) {
                identityInput.setError("Önce dijital anahtarı etkinleştirin.");
                return;
            }
            if (!saveInputs(storage, manager, vinInput, identityInput)) return;
            markPending.run();
            manager.authenticateSmartAccess(callback);
        });
        create.setOnClickListener(view -> {
            if (!saveInputs(storage, manager, vinInput, identityInput)) return;
            markPending.run();
            manager.createSmartAccessCertificate(identityInput.getText().toString().trim(), callback);
        });
        login.setOnClickListener(view -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ToggLoginActivity.class));
        });
        guestLogin.setOnClickListener(view -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ToggLoginActivity.class)
                    .putExtra(ToggLoginActivity.EXTRA_SMART_ACCESS_ACCOUNT, true));
        });
        invitationPreview.setOnClickListener(view -> {
            EditText linkInput = new EditText(activity);
            linkInput.setHint("Trumore davet bağlantısı");
            linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            new AlertDialog.Builder(activity).setTitle("Davet bilgilerini incele")
                    .setMessage("Davet bağlantısını yapıştırın. Bu adım daveti kabul etmez.")
                    .setView(linkInput).setNegativeButton("İptal", null)
                    .setPositiveButton("Bilgileri al", (prompt, which) -> {
                        String selectedLink = linkInput.getText().toString();
                        selectedInvitation[0] = null;
                        acceptInvitation.setEnabled(false);
                        invitationPreview.setEnabled(false);
                        status.setText("Davet bilgileri alınıyor...");
                        new SmartAccessInvitationClient(activity).preview(selectedLink,
                                new SmartAccessInvitationClient.Callback() {
                            private void show(String message) {
                                activity.runOnUiThread(() -> {
                                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                                    invitationPreview.setEnabled(true);
                                    status.setText(AppLog.userMessage(activity, "KEY", message, "İşlem tamamlanamadı. Hesabınızı, davetin geçerliliğini ve araç sahibi onayını kontrol edin."));
                                });
                            }
                            @Override public void onPreview(String message) {
                                activity.runOnUiThread(() -> {
                                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                                    selectedInvitation[0] = selectedLink;
                                    acceptInvitation.setEnabled(true);
                                });
                                show(message);
                            }
                            @Override public void onError(String message) { show(message); }
                        });
                    }).show();
        });
        SmartAccessInvitationClient.AccessCallback guestCallback = new SmartAccessInvitationClient.AccessCallback() {
            private void show(String message) {
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                    invitationPreview.setEnabled(true);
                    guestCertificate.setEnabled(true);
                    guestLogin.setEnabled(true);
                    status.setText(AppLog.userMessage(activity, "KEY", message, "İşlem tamamlanamadı. Hesabınızı, davetin geçerliliğini ve araç sahibi onayını kontrol edin."));
                });
            }
            @Override public void onAccepted(String message) { AppLog.event(activity, "KEY", message); show("Davet kabul edildi. Araç sahibi onayını tamamlayıp paylaşılan anahtarı etkinleştirin."); }
            @Override public void onError(String message) { show(message); }
            @Override public void onCertificate(com.togg.companion.core.SmartAccessCertificate value) {
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                    identityInput.setText(value.truId);
                    owner.setChecked(false);
                    manager.useSharedCertificate(value, (success, message) -> {
                        AppLog.event(activity, "KEY", message);
                        show(success ? "Paylaşılan anahtar hazır. Aracın yakınında bağlantı kurun." : "Anahtar etkinleştirilemedi. Hesabı ve araç sahibi onayını kontrol edin.");
                    });
                });
            }
        };
        Runnable guestPending = () -> {
            acceptInvitation.setEnabled(false);
            guestCertificate.setEnabled(false);
            invitationPreview.setEnabled(false);
            guestLogin.setEnabled(false);
            status.setText("Paylaşılan anahtar işlemi sürüyor...");
        };
        acceptInvitation.setOnClickListener(view -> {
            String link = selectedInvitation[0];
            if (link == null) return;
            guestPending.run();
            new SmartAccessInvitationClient(activity).accept(link, storage.getVin(), guestCallback);
        });
        guestCertificate.setOnClickListener(view -> {
            guestPending.run();
            new SmartAccessInvitationClient(activity).fetchCertificate(storage.getVin(), guestCallback);
        });
        dialog.setOnShowListener(ignored -> manager.addAccessListener(listener));
        dialog.setOnDismissListener(ignored -> manager.removeAccessListener(listener));
        dialog.show();
        return dialog;
    }

    private static boolean saveInputs(SecureTokenStorage storage, VehicleBleManager manager,
                                      EditText vinInput, EditText identityInput) {
        String vin = vinInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            vinInput.setError("17 haneli gerçek VIN girin.");
            return false;
        }
        if (identityInput.getText().toString().trim().isEmpty()) {
            identityInput.setError("Hesap bilgileri eksik. Togg ID ile yeniden giriş yapın.");
            return false;
        }
        if (!vin.equals(storage.getVin()) || manager.isDemoConnected()) manager.disconnect();
        storage.setVin(vin);
        storage.setDemoMode(false);
        return true;
    }
}
