package com.togg.companion.service;

import android.app.job.JobParameters;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.pm.PackageManager;
import android.Manifest;
import com.togg.companion.R;
import com.togg.companion.core.GezRouteBridge;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.AbrpClient;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.core.VehicleTelemetry;

public class AbrpJobService extends JobService {
    public static final int JOB_ID = 8201;
    public static final int ROUTE_JOB_ID = 8202;
    public static final long INTERVAL_MILLIS = 7 * 60 * 1000L;
    public static final long ROUTE_INTERVAL_MILLIS = 15 * 60 * 1000L;

    public static void scheduleNext(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        SecureTokenStorage storage = new SecureTokenStorage(context);
        if (!storage.isAbrpEnabled()) { scheduler.cancel(JOB_ID); return; }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, AbrpJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(INTERVAL_MILLIS)
                .setOverrideDeadline(INTERVAL_MILLIS + 120_000L)
                .build();
        scheduler.schedule(job);
    }

    public static void scheduleNextRouteCheck(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        SecureTokenStorage storage = new SecureTokenStorage(context);
        if (!storage.isAbrpRouteSyncEnabled() || storage.getAbrpToken().isEmpty()) { scheduler.cancel(ROUTE_JOB_ID); return; }
        JobInfo job = new JobInfo.Builder(ROUTE_JOB_ID, new ComponentName(context, AbrpJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(ROUTE_INTERVAL_MILLIS)
                .setOverrideDeadline(ROUTE_INTERVAL_MILLIS + 120_000L)
                .build();
        scheduler.schedule(job);
    }

    @Override public boolean onStartJob(JobParameters params) {
        SecureTokenStorage storage = new SecureTokenStorage(this);
        if (params.getJobId() == ROUTE_JOB_ID) {
            if (!storage.isAbrpRouteSyncEnabled() || storage.getAbrpToken().isEmpty()) { jobFinished(params, false); return false; }
            runRouteCheck(params, storage);
            return true;
        }
        if (!storage.isAbrpEnabled()) { jobFinished(params, false); return false; }
        new TvaApiClient(storage).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
            @Override public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                storage.saveActiveTelemetry(telemetry);
                boolean sent = AbrpClient.sendTelemetry(storage.getAbrpToken(), telemetry, storage.getAbrpCapacity());
                if (sent) storage.setAbrpLastSync(System.currentTimeMillis());
                scheduleNext(AbrpJobService.this);
                jobFinished(params, false);
            }
            @Override public void onError(String message, VehicleTelemetry fallback) {
                scheduleNext(AbrpJobService.this);
                jobFinished(params, false);
            }
        });
        return true;
    }

    private void runRouteCheck(JobParameters params, SecureTokenStorage storage) {
        new Thread(() -> {
            try {
                com.togg.companion.data.remote.AbrpClient.AbrpPlanStop stop = com.togg.companion.data.remote.AbrpClient.fetchLatestPlan(storage.getAbrpToken());
                storage.setAbrpLastRouteCheck(System.currentTimeMillis());
                String key = stop.key();
                if (key.equals(storage.getAbrpLastDispatchedStop())) {
                    storage.setAbrpActiveStopName(stop.name);
                    scheduleNextRouteCheck(this); jobFinished(params, false); return;
                }
                new TvaApiClient(storage).sendDestinationToVehicle(stop.lat, stop.lon, stop.name, new TvaApiClient.DestinationCallback() {
                    @Override public void onSuccess(String message) {
                        storage.setAbrpLastDispatchedStop(key); storage.setAbrpActiveStopName(stop.name);
                        postRouteNotification(stop.name, "Yeni ABRP durağı aracınıza iletildi.");
                        scheduleNextRouteCheck(AbrpJobService.this); jobFinished(params, false);
                    }
                    @Override public void onError(String message) {
                        boolean fallbackOpened = GezRouteBridge.openCarDisplayDirections(AbrpJobService.this, stop.lat, stop.lon, stop.name);
                        if (fallbackOpened) {
                            storage.setAbrpLastDispatchedStop(key); storage.setAbrpActiveStopName(stop.name);
                            postRouteNotification(stop.name, "Togg Cloud kullanılamadı; Trumore Gez açıldı.");
                        } else postRouteNotification(stop.name, "Rota bulundu ancak araca gönderilemedi: " + message);
                        scheduleNextRouteCheck(AbrpJobService.this); jobFinished(params, false);
                    }
                });
            } catch (Exception error) {
                storage.setAbrpLastRouteCheck(System.currentTimeMillis());
                scheduleNextRouteCheck(this); jobFinished(params, false);
            }
        }, "abrp-route-check").start();
    }

    private void postRouteNotification(String stopName, String message) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channelId = "abrp_route_updates";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channelId, "ABRP rota güncellemeleri", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("ABRP yeni rota durağı: " + stopName).setContentText(message).setAutoCancel(true).build();
        manager.notify(ROUTE_JOB_ID, notification);
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
