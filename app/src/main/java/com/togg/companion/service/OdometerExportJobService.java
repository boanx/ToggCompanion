package com.togg.companion.service;

import android.app.job.*;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import com.togg.companion.core.AppLog;
import com.togg.companion.core.OdometerExport;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.OdometerExportStore;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.TvaApiClient;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persisted, inexact background writes to the user-selected document provider (e.g. Drive). */
public final class OdometerExportJobService extends JobService {
    private static final int SCHEDULED = 8204, MANUAL = 8205;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.Map<JobParameters, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Session {
        final AtomicBoolean active = new AtomicBoolean(true);
        final CancellationSignal cancellation = new CancellationSignal();
        boolean ownsGate;
        volatile ParcelFileDescriptor descriptor;
        void close() {
            cancellation.cancel();
            ParcelFileDescriptor file = descriptor;
            if (file != null) try { file.close(); } catch (Exception ignored) { }
        }
    }

    public static boolean schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return false;
        OdometerExportStore config = new OdometerExportStore(context);
        jobs.cancel(SCHEDULED);
        config.next(0);
        if (!config.enabled()) return true;
        try {
            long next = OdometerExport.nextTime(config.first(), config.minutes(), System.currentTimeMillis());
            boolean ok = jobs.schedule(builder(context, SCHEDULED, config).setPersisted(true)
                    .setMinimumLatency(Math.max(1, next - System.currentTimeMillis())).build()) == JobScheduler.RESULT_SUCCESS;
            if (ok) config.next(next);
            return ok;
        } catch (Exception error) { AppLog.event(context, "ODOMETER", "SCHEDULE_FAILED " + error.getClass().getSimpleName()); return false; }
    }
    public static boolean sendNow(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null || BUSY.get() || jobs.getPendingJob(MANUAL) != null) return false;
        try {
            return jobs.schedule(builder(context, MANUAL, new OdometerExportStore(context)).setMinimumLatency(1).build()) == JobScheduler.RESULT_SUCCESS;
        } catch (Exception error) { return false; }
    }
    private static JobInfo.Builder builder(Context context, int id, OdometerExportStore config) {
        PersistableBundle extras = new PersistableBundle(); extras.putString("generation", config.generation());
        return new JobInfo.Builder(id, new ComponentName(context, OdometerExportJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(extras)
                .setBackoffCriteria(60000, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
    }

    @Override public boolean onStartJob(JobParameters params) {
        Session run = new Session();
        if (!BUSY.compareAndSet(false, true)) { main.post(() -> jobFinished(params, true)); return true; }
        run.ownsGate = true; sessions.put(params, run);
        main.postDelayed(() -> finish(params, run, false, "Gönderim zaman aşımına uğradı."), 120000);
        new Thread(() -> {
            try {
                OdometerExportStore config = new OdometerExportStore(this);
                SecureTokenStorage auth = new SecureTokenStorage(this);
                String generation = params.getExtras().getString("generation", "");
                String vin = auth.getVin(), scope = config.scope(), target = config.uri();
                if (!current(params, run, config, auth, generation, scope) || target.isEmpty()) {
                    finish(params, run, false, "Gönderim ayarlarını ve Togg ID bağlantısını kontrol edin."); return;
                }
                AppLog.event(this, "ODOMETER", "FRESH_FETCH_START");
                new TvaApiClient(auth).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
                    @Override public void onSuccess(VehicleTelemetry telemetry, boolean live) {
                        try {
                            if (!live || telemetry == null || !vin.equalsIgnoreCase(telemetry.vin)
                                    || !current(params, run, config, auth, generation, scope)) {
                                finish(params, run, false, "Güncel araç verisi alınamadı."); return;
                            }
                            // A fresh HTTP fetch can return an old cloud snapshot. Preserve its actual timestamp.
                            if (telemetry.odometerKm <= 0 || telemetry.timestampMillis <= 0) {
                                finish(params, run, false, "Kilometre veya araç veri zamanı bilinmiyor."); return;
                            }
                            byte[] json = OdometerExport.json(telemetry.odometerKm, telemetry.timestampMillis,
                                    System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                            if (!current(params, run, config, auth, generation, scope)) { finish(params, run, false, "Gönderim iptal edildi."); return; }
                            try (ParcelFileDescriptor file = getContentResolver().openFileDescriptor(Uri.parse(target), "rwt", run.cancellation)) {
                                if (file == null) throw new java.io.IOException("No file descriptor");
                                run.descriptor = file;
                                run.cancellation.throwIfCanceled();
                                try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(file)) {
                                    output.write(json); output.flush();
                                }
                            } finally { run.descriptor = null; }
                            boolean stillCurrent = current(params, run, config, auth, generation, scope);
                            finish(params, run, stillCurrent, stillCurrent
                                    ? "Dosya güncellendi. Bulut eşitlemesini dosya sağlayıcısı tamamlar."
                                    : "Gönderim sırasında ayarlar değişti; dosyayı kontrol edin.");
                        } catch (Exception error) {
                            AppLog.event(OdometerExportJobService.this, "ODOMETER", "WRITE_FAILED " + error.getClass().getSimpleName());
                            finish(params, run, false, "Dosya güncellenemedi. Drive bağlantısını ve dosya erişimini kontrol edin.");
                        }
                    }
                    @Override public void onError(String message, VehicleTelemetry fallback) {
                        AppLog.event(OdometerExportJobService.this, "ODOMETER", "FETCH_FAILED " + message);
                        finish(params, run, false, "Araç verisi alınamadı; mevcut dosya korundu.");
                    }
                });
            } catch (Exception error) { finish(params, run, false, "Gönderim başlatılamadı."); }
        }, "odometer-export").start();
        return true;
    }
    private static boolean current(JobParameters params, Session run, OdometerExportStore config,
                                   SecureTokenStorage auth, String generation, String scope) {
        return run.active.get() && generation.equals(config.generation()) && !scope.isEmpty()
                && scope.equals(OdometerExportStore.accountScope(auth)) && auth.hasValidToken()
                && !auth.isDemoMode() && !auth.isDemoVin() && (params.getJobId() == MANUAL || config.enabled());
    }
    private void finish(JobParameters params, Session run, boolean success, String message) {
        main.post(() -> {
            if (!run.active.compareAndSet(true, false)) return;
            run.close();
            sessions.remove(params, run);
            if (run.ownsGate) BUSY.set(false);
            OdometerExportStore config = new OdometerExportStore(this);
            if (params.getExtras().getString("generation", "").equals(config.generation())) config.result(success, message);
            AppLog.event(this, "ODOMETER", success ? "DOCUMENT_WRITE_OK" : "EXPORT_FAILED");
            jobFinished(params, false);
            if (params.getJobId() == SCHEDULED) schedule(this);
        });
    }
    @Override public boolean onStopJob(JobParameters params) {
        Session run = sessions.remove(params);
        if (run != null && run.active.compareAndSet(true, false)) {
            run.close(); if (run.ownsGate) BUSY.set(false);
        }
        return true; // Android retries interrupted work; obsolete generations are rejected on start.
    }
}
