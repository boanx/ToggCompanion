package com.togg.companion.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.AbrpClient;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.core.VehicleTelemetry;

public class AbrpJobService extends JobService {
    public static final int JOB_ID = 8201;
    public static final long INTERVAL_MILLIS = 15 * 60 * 1000L;

    @Override public boolean onStartJob(JobParameters params) {
        SecureTokenStorage storage = new SecureTokenStorage(this);
        if (!storage.isAbrpEnabled()) { jobFinished(params, false); return false; }
        new TvaApiClient(storage).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
            @Override public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                storage.saveActiveTelemetry(telemetry);
                boolean sent = AbrpClient.sendTelemetry(storage.getAbrpToken(), telemetry, storage.getAbrpCapacity());
                if (sent) storage.setAbrpLastSync(System.currentTimeMillis());
                jobFinished(params, false);
            }
            @Override public void onError(String message, VehicleTelemetry fallback) { jobFinished(params, true); }
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
