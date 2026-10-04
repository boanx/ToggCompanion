package com.togg.companion.service;

import android.app.job.JobParameters;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.AbrpClient;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.core.VehicleTelemetry;

public class AbrpJobService extends JobService {
    public static final int JOB_ID = 8201;
    public static final long INTERVAL_MILLIS = 7 * 60 * 1000L;

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

    @Override public boolean onStartJob(JobParameters params) {
        SecureTokenStorage storage = new SecureTokenStorage(this);
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

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
