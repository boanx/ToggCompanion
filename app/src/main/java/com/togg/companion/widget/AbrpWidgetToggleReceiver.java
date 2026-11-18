package com.togg.companion.widget;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.togg.companion.core.AppLog;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.service.AbrpSyncService;
/** Private target reached only through the app-created widget PendingIntent. */
public final class AbrpWidgetToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SecureTokenStorage storage = new SecureTokenStorage(context);
        boolean active = storage.isAbrpEnabled() && AbrpSyncService.isRunning();
        if (active) {
            AbrpSyncService.stop(context);
            AppLog.event(context, "ABRP", "WIDGET_DISABLED");
        } else if (storage.hasValidToken() && !storage.isDemoMode() && !storage.isDemoVin() && !storage.getAbrpToken().isEmpty()) {
            storage.setAbrpEnabled(true);
            if (!AbrpSyncService.start(context)) Toast.makeText(context, "ABRP başlatılamadı. Yol Tarifi sayfasını açıp yeniden deneyin.", Toast.LENGTH_LONG).show();
            AppLog.event(context, "ABRP", "WIDGET_ENABLE_REQUESTED");
        } else Toast.makeText(context, "Önce Yol Tarifi sayfasından araç ve ABRP bağlantısını tamamlayın.", Toast.LENGTH_LONG).show();
        ToggAppWidgetProvider.updateAllWidgets(context);
    }
}
