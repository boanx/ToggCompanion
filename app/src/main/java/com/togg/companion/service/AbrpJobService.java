package com.togg.companion.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.AbrpClient;
import com.togg.companion.data.remote.TvaApiClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AbrpJobService extends JobService {
    public static final int JOB_ID = 8201;
    public static final int ROUTE_JOB_ID = 8202;
    public static final long INTERVAL_MILLIS = 4 * 60 * 1000L;
    public static final long ROUTE_INTERVAL_MILLIS = INTERVAL_MILLIS;
    private static final AtomicBoolean ROUTE_RUNNING = new AtomicBoolean();
    private final ConcurrentHashMap<Integer, Run> runs = new ConcurrentHashMap<>();
    private static final class Run {
        final JobParameters params;
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicBoolean stopped = new AtomicBoolean();
        final String scope;
        final boolean route;
        Run(JobParameters params, String scope, boolean route) { this.params = params; this.scope = scope; this.route = route; }
    }

    public static void scheduleNext(Context context) { schedule(context, JOB_ID, INTERVAL_MILLIS); }
    public static void scheduleNextRouteCheck(Context context) { schedule(context, ROUTE_JOB_ID, ROUTE_INTERVAL_MILLIS); }
    public static void requestRouteCheck(Context context) { schedule(context, ROUTE_JOB_ID, 0); }
    private static void schedule(Context context, int id, long delay) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        SecureTokenStorage storage = new SecureTokenStorage(context);
        boolean enabled = id == ROUTE_JOB_ID ? storage.isAbrpRouteSyncEnabled() : storage.isAbrpEnabled();
        if (!enabled || storage.getAbrpToken().isEmpty()) { scheduler.cancel(id); return; }
        JobInfo job = new JobInfo.Builder(id, new ComponentName(context, AbrpJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay)
                .setOverrideDeadline(delay + 120_000L).build();
        scheduler.schedule(job);
    }

    @Override public boolean onStartJob(JobParameters params) {
        SecureTokenStorage storage = new SecureTokenStorage(this);
        boolean route = params.getJobId() == ROUTE_JOB_ID;
        if (storage.getAbrpToken().isEmpty() || !(route ? storage.isAbrpRouteSyncEnabled() : storage.isAbrpEnabled())) return false;
        if (route && (storage.isDemoMode() || !storage.hasValidToken() || storage.isDemoVin())) {
            storage.setAbrpRouteStatus("Otomatik rota için gerçek VIN ve Togg ID oturumu gerekli.");
            scheduleNextRouteCheck(this); return false;
        }
        if (route && !ROUTE_RUNNING.compareAndSet(false, true)) { scheduleNextRouteCheck(this); return false; }
        Run run;
        try { run = new Run(params, storage.getAbrpRouteScope(), route); }
        catch (Exception invalid) { if (route) ROUTE_RUNNING.set(false); return false; }
        runs.put(params.getJobId(), run);
        if (route) runRouteCheck(run, storage);
        else new TvaApiClient(storage).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
            @Override public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                try {
                    if (isLive && current(run, storage, false)) {
                        storage.saveActiveTelemetry(telemetry);
                        if (AbrpClient.sendTelemetry(storage.getAbrpToken(), telemetry, storage.getAbrpCapacity())
                                && current(run, storage, false)) storage.setAbrpLastSync(System.currentTimeMillis());
                    }
                } finally { finish(run); }
            }
            @Override public void onError(String message, VehicleTelemetry fallback) { finish(run); }
        });
        return true;
    }

    private void runRouteCheck(Run run, SecureTokenStorage storage) {
        new Thread(() -> {
            try {
                if (!current(run, storage, true)) { finish(run); return; }
                storage.setAbrpLastRouteCheck(System.currentTimeMillis());
                AbrpClient.AbrpRoutePlan plan = AbrpClient.fetchLatestRoute(storage.getAbrpToken());
                if (!current(run, storage, true)) { finish(run); return; }
                storage.setAbrpActiveStopName(plan.summary());
                if (plan.fingerprint.equals(storage.getAbrpAcceptedRoute(run.scope))) {
                    storage.setAbrpRouteStatus("Rota değişmedi; tekrar gönderilmedi."); finish(run); return;
                }
                if (plan.fingerprint.equals(storage.getAbrpPendingRoute(run.scope))) {
                    status(storage, "Önceki gönderimin sonucu belirsiz. Araç ekranını kontrol edin; aynı rota otomatik tekrarlanmadı.", true);
                    finish(run); return;
                }
                if (!storage.markAbrpRouteAttempt(run.scope, plan.fingerprint)) {
                    status(storage, "Rota kaydı saklanamadı; gönderim başlatılmadı.", true); finish(run); return;
                }
                if (!current(run, storage, true)) { storage.clearAbrpRouteAttempt(run.scope); finish(run); return; }
                storage.setAbrpRouteStatus("Yeni ABRP rotası gönderiliyor…");
                new TvaApiClient(storage).sendRouteToVehicle(plan.toVehicleRoute(), new TvaApiClient.DestinationCallback() {
                    @Override public void onSuccess(String message) {
                        try {
                            if (sameScope(run, storage)) {
                                boolean saved = storage.recordAbrpRouteAccepted(run.scope, plan.fingerprint, plan.summary());
                                status(storage, message + (saved ? " " + plan.summary() : " Kabul kaydı saklanamadı; araç ekranını kontrol edin."), true);
                            }
                        } finally { finish(run); }
                    }
                    @Override public void onError(String message) {
                        try {
                            if (sameScope(run, storage)) {
                                storage.clearAbrpRouteAttempt(run.scope);
                                status(storage, "Rota gönderilemedi: " + message, true);
                            }
                        } finally { finish(run); }
                    }
                    @Override public void onUncertain(String message) {
                        try {
                            if (sameScope(run, storage)) status(storage, message + " Aynı rota otomatik tekrar gönderilmeyecek.", true);
                        } finally { finish(run); }
                    }
                });
            } catch (Exception error) {
                if (current(run, storage, true)) status(storage, "ABRP rota bilgisi alınamadı. Token, API erişimi ve bağlantıyı kontrol edin.", true);
                finish(run);
            }
        }, "abrp-route-check").start();
    }

    private boolean sameScope(Run run, SecureTokenStorage storage) {
        try { return run.scope.equals(storage.getAbrpRouteScope()); }
        catch (Exception unavailable) { return false; }
    }
    private boolean current(Run run, SecureTokenStorage storage, boolean route) {
        return !run.stopped.get() && !run.finished.get() && sameScope(run, storage)
                && (route ? storage.isAbrpRouteSyncEnabled() && !storage.isDemoMode() && storage.hasValidToken() && !storage.isDemoVin() : storage.isAbrpEnabled());
    }
    private void finish(Run run) {
        if (!run.finished.compareAndSet(false, true)) return;
        runs.remove(run.params.getJobId(), run);
        if (run.route) { ROUTE_RUNNING.set(false); scheduleNextRouteCheck(this); }
        else scheduleNext(this);
        if (!run.stopped.get()) jobFinished(run.params, false);
    }
    private void status(SecureTokenStorage storage, String message, boolean notify) {
        boolean changed = !message.equals(storage.getAbrpRouteStatus());
        storage.setAbrpRouteStatus(message);
        if (notify && changed) postRouteNotification(message);
    }
    private void postRouteNotification(String message) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channelId = "abrp_route_updates";
        manager.createNotificationChannel(new NotificationChannel(channelId, "ABRP rota güncellemeleri", NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification = new Notification.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("ABRP rota aktarımı").setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true).build();
        manager.notify(ROUTE_JOB_ID, notification);
    }
    @Override public boolean onStopJob(JobParameters params) {
        Run run = runs.get(params.getJobId());
        if (run != null) run.stopped.set(true);
        if (params.getJobId() == ROUTE_JOB_ID) scheduleNextRouteCheck(this); else scheduleNext(this);
        return false;
    }
}
