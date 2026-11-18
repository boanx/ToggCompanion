package com.togg.companion.widget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.togg.companion.core.AppLog;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.data.remote.TvaApiClient;
import java.util.concurrent.atomic.AtomicBoolean;

/** One user-requested live telemetry fetch, independent of Activity and broadcast lifetime. */
public final class WidgetRefreshJobService extends JobService {
    private static final int JOB_ID = 8203;
    private static volatile boolean running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AtomicBoolean active;

    public static boolean request(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return false;
        // Repeated taps share the pending/running request instead of cancelling its callback.
        if (running || jobs.getPendingJob(JOB_ID) != null) return true;
        ComponentName component = new ComponentName(context, WidgetRefreshJobService.class);
        boolean accepted = false;
        if (Build.VERSION.SDK_INT >= 31) {
            try { accepted = jobs.schedule(new JobInfo.Builder(JOB_ID, component).setExpedited(true).build()) == JobScheduler.RESULT_SUCCESS; }
            catch (RuntimeException ignored) { }
        }
        if (!accepted) {
            try { accepted = jobs.schedule(new JobInfo.Builder(JOB_ID, component).setOverrideDeadline(0).build()) == JobScheduler.RESULT_SUCCESS; }
            catch (RuntimeException ignored) { }
        }
        running = accepted;
        AppLog.event(context, "WIDGET", accepted ? "REFRESH_REQUESTED" : "REFRESH_REJECTED");
        ToggAppWidgetProvider.updateAllWidgets(context);
        return accepted;
    }
    public static boolean isRefreshing(Context context) { return running; }
    @Override public boolean onStartJob(JobParameters params) {
        AtomicBoolean session = new AtomicBoolean(true);
        active = session; running = true;
        // Encrypted storage/network initialization runs away from JobService's main-thread callbacks.
        new Thread(() -> {
            try {
                SecureTokenStorage storage = new SecureTokenStorage(this);
                String vin = storage.getVin();
                if (!storage.hasValidToken() || storage.isDemoMode() || storage.isDemoVin()) {
                    finish(params, session, "SESSION_UNAVAILABLE", false); return;
                }
                ToggAppWidgetProvider.updateAllWidgets(this);
                AppLog.event(this, "WIDGET", "FRESH_FETCH_START");
                new TvaApiClient(storage).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
                    @Override public void onSuccess(VehicleTelemetry telemetry, boolean live) {
                        if (!session.get()) return;
                        try {
                            if (!live || telemetry == null || !vin.equalsIgnoreCase(telemetry.vin)
                                    || !vin.equalsIgnoreCase(storage.getVin()) || !storage.hasValidToken() || storage.isDemoMode()) {
                                finish(params, session, "IGNORED_NONLIVE_OR_CHANGED_CONTEXT", false); return;
                            }
                            storage.saveActiveTelemetry(telemetry);
                            try (TelemetryDatabaseHelper db = new TelemetryDatabaseHelper(WidgetRefreshJobService.this)) { db.insert(telemetry); }
                            if (telemetry.timestampMillis > 0) storage.setLastSync(telemetry.timestampMillis);
                            storage.setLastRefresh(System.currentTimeMillis());
                            finish(params, session, "FRESH_FETCH_OK ageMs=" + (telemetry.timestampMillis > 0
                                    ? Math.max(0, System.currentTimeMillis() - telemetry.timestampMillis) : -1), true);
                        } catch (Exception error) { finish(params, session, "SAVE_FAILED " + error.getClass().getSimpleName(), false); }
                    }
                    @Override public void onError(String message, VehicleTelemetry fallback) {
                        // Never write fallback/demo or advance timestamps when the live request fails.
                        finish(params, session, "FRESH_FETCH_FAILED " + message, false);
                    }
                });
            } catch (Exception error) { finish(params, session, "REFRESH_FAILED " + error.getClass().getSimpleName(), false); }
        }, "WidgetTelemetryFetch").start();
        return true;
    }
    private void finish(JobParameters params, AtomicBoolean session, String event, boolean success) {
        main.post(() -> {
            if (!session.compareAndSet(true, false)) return;
            running = false;
            AppLog.event(this, "WIDGET", event);
            jobFinished(params, false);
            ToggAppWidgetProvider.updateAllWidgets(this);
            if (!success) android.widget.Toast.makeText(this, "Araç verisi alınamadı. Önceki veri korunuyor.", android.widget.Toast.LENGTH_SHORT).show();
        });
    }
    @Override public boolean onStopJob(JobParameters params) {
        if (active != null) active.set(false);
        running = false;
        AppLog.event(this, "WIDGET", "REFRESH_STOPPED");
        main.post(() -> ToggAppWidgetProvider.updateAllWidgets(this));
        return false;
    }
}
