package com.togg.companion.service;

import android.app.*;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.togg.companion.core.AppLog;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.ui.MainActivity;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started, visible data-sync session. Android may impose a background time limit. */
public final class AbrpSyncService extends Service {
    public static final long INTERVAL_MILLIS = 60_000L;
    private static final int NOTIFICATION_ID = 8201;
    private static final String CHANNEL = "abrp_telemetry";
    private static final String STOP = "com.togg.companion.STOP_ABRP";
    private static volatile boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean active = new AtomicBoolean();
    private PowerManager.WakeLock wakeLock;
    private SecureTokenStorage storage;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active.get() || !storage.isAbrpEnabled()) { stopSelf(); return; }
            if (!storage.hasValidToken() || storage.isDemoMode() || storage.isDemoVin() || storage.getAbrpToken().isEmpty()) {
                AppLog.event(AbrpSyncService.this, "ABRP", "SESSION_UNAVAILABLE_STOP"); stopSelf(); return;
            }
            AbrpSyncEngine.send(AbrpSyncService.this, () -> active.get() && storage.isAbrpEnabled(), ok -> { });
            handler.postDelayed(this, INTERVAL_MILLIS);
        }
    };
    public static boolean isRunning() { return running; }
    public static void cancelLegacyJobs(Context context) {
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobs != null) { jobs.cancel(8201); jobs.cancel(8202); }
    }
    public static boolean start(Context context) {
        cancelLegacyJobs(context);
        try {
            context.startForegroundService(new Intent(context, AbrpSyncService.class));
            return true;
        } catch (Exception error) {
            new SecureTokenStorage(context).setAbrpEnabled(false);
            AppLog.event(context, "ABRP", "SERVICE_START_DENIED " + error.getClass().getSimpleName());
            return false;
        }
    }
    public static void stop(Context context) {
        new SecureTokenStorage(context).setAbrpEnabled(false);
        cancelLegacyJobs(context);
        context.stopService(new Intent(context, AbrpSyncService.class));
    }
    @Override public void onCreate() { super.onCreate(); storage = new SecureTokenStorage(this); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { storage.setAbrpEnabled(false); stopSelf(); return START_NOT_STICKY; }
        if (!storage.isAbrpEnabled()) { stopSelf(); return START_NOT_STICKY; }
        if (active.get()) return START_NOT_STICKY;
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "ABRP veri aktarımı", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_DIRECTIONS, true), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AbrpSyncService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("ABRP veri aktarımı aktif")
                    .setContentText("Araç verileri dakikada bir güncelleniyor.").setContentIntent(open)
                    .setOngoing(true).setOnlyAlertOnce(true).addAction(android.R.drawable.ic_media_pause, "Durdur", stop).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION_ID, notification);
            PowerManager power = getSystemService(PowerManager.class);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ToggCompanion:ABRP");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(6 * 60 * 60 * 1000L);
            active.set(true); running = true;
            com.togg.companion.widget.ToggAppWidgetProvider.updateAllWidgets(this);
            handler.post(tick);
            // Bound CPU wake time as well as honoring the platform's own quota.
            handler.postDelayed(() -> { AppLog.event(this, "ABRP", "SESSION_TIME_LIMIT"); stopSelf(); }, 6 * 60 * 60 * 1000L);
            AppLog.event(this, "ABRP", "SERVICE_STARTED intervalMs=60000");
        } catch (Exception error) {
            AppLog.event(this, "ABRP", "SERVICE_FAILED " + error.getClass().getSimpleName()); stopSelf();
        }
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId, int fgsType) {
        AppLog.event(this, "ABRP", "ANDROID_DATA_SYNC_TIMEOUT");
        active.set(false); storage.setAbrpEnabled(false); stopSelf();
    }
    @Override public void onDestroy() {
        active.set(false); running = false; handler.removeCallbacksAndMessages(null);
        storage.setAbrpEnabled(false);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        com.togg.companion.widget.ToggAppWidgetProvider.updateAllWidgets(this);
        AppLog.event(this, "ABRP", "SERVICE_STOPPED"); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
