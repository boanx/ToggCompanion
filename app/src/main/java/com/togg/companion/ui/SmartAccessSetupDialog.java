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
import com.togg.companion.data.SecureTokenStorage;

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
        explanation.setText("Togg ID hesabınızın araç erişimi kullanılır. Kullanıcı kimliğinizi ve aracın 17 haneli VIN bilgisini doğrulayın; sertifika sunucudan alınır. Kaydedilmiş davet bağlantısı tek başına araç erişimi sağlamaz.");
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

        CheckBox owner = new CheckBox(activity);
        owner.setText("Aracın sahibiyim");
        owner.setChecked(storage.getOwnerId().isEmpty());
        content.addView(owner);
        TextView ownerNote = new TextView(activity);
        ownerNote.setText("Paylaşılan araçta bu seçeneği kapatın. Erişim yetkisini sunucu doğrular.");
        content.addView(ownerNote);

        TextView status = new TextView(activity);
        status.setPadding(0, padding / 2, 0, padding / 2);
        status.setText(manager.getAccessStatusText());
        content.addView(status);

        Button fetch = new Button(activity);
        fetch.setText("Sertifikayı al ve yetkilendir");
        content.addView(fetch);
        Button authenticate = new Button(activity);
        authenticate.setText("Kayıtlı anahtarla oturumu aç");
        content.addView(authenticate);
        Button create = new Button(activity);
        create.setText("Yeni Smart Access sertifikası oluştur");
        content.addView(create);
        Button login = new Button(activity);
        login.setText("Togg ID ile giriş yap");
        content.addView(login);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Smart Access kurulumu")
                .setView(scroll)
                .setNegativeButton("Kapat", null)
                .create();
        VehicleBleManager.OnAccessStateChangeListener listener = state -> activity.runOnUiThread(() -> {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            status.setText(manager.getAccessStatusText());
            onStatusChanged.run();
        });
        VehicleBleManager.AccessCallback callback = (success, message) -> activity.runOnUiThread(() -> {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            fetch.setEnabled(true);
            authenticate.setEnabled(true);
            create.setEnabled(true);
            status.setText(message + "\n" + manager.getAccessStatusText());
            onStatusChanged.run();
        });
        Runnable markPending = () -> {
            fetch.setEnabled(false);
            authenticate.setEnabled(false);
            create.setEnabled(false);
            status.setText("İşlem sürüyor...");
        };
        fetch.setOnClickListener(view -> {
            if (!saveInputs(storage, manager, vinInput, identityInput)) return;
            markPending.run();
            manager.prepareSmartAccess(identityInput.getText().toString().trim(), owner.isChecked(), callback);
        });
        authenticate.setOnClickListener(view -> {
            String configuredIdentity = manager.getConfiguredTruId();
            if (configuredIdentity == null || !identityInput.getText().toString().trim().equals(configuredIdentity)) {
                identityInput.setError("Bu kullanıcı için önce sertifikayı alın.");
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
            identityInput.setError("Trumore kullanıcı kimliği gerekli.");
            return false;
        }
        if (!vin.equals(storage.getVin()) || manager.isDemoConnected()) manager.disconnect();
        storage.setVin(vin);
        storage.setDemoMode(false);
        return true;
    }
}
