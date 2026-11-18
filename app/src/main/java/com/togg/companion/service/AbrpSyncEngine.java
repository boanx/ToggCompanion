package com.togg.companion.service;

import android.content.Context;
import com.togg.companion.core.AppLog;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.AbrpClient;
import com.togg.companion.data.remote.TvaApiClient;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One fresh vehicle fetch per upload; foreground/manual sends share this gate. */
public final class AbrpSyncEngine {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private AbrpSyncEngine() { }
    public static void send(Context context, BooleanSupplier active, Consumer<Boolean> complete) {
        Context app = context.getApplicationContext();
        SecureTokenStorage storage = new SecureTokenStorage(app);
        if (!BUSY.compareAndSet(false, true)) { complete.accept(false); return; }
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<Boolean> finish = success -> {
            if (finished.compareAndSet(false, true)) { BUSY.set(false); complete.accept(success); }
        };
        try {
            final String scope = storage.getAbrpSyncScope(), vin = storage.getVin(), token = storage.getAbrpToken();
            final double capacity = storage.getAbrpCapacity();
            BooleanSupplier current = () -> active.getAsBoolean() && !storage.isDemoMode() && !storage.isDemoVin()
                    && storage.hasValidToken() && scope.equals(storage.getAbrpSyncScope());
            if (token.isEmpty() || !current.getAsBoolean()) { finish.accept(false); return; }
            AppLog.event(app, "ABRP", "FRESH_FETCH_START");
            new TvaApiClient(storage).fetchTelemetry(new TvaApiClient.TelemetryCallback() {
                @Override public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                    boolean ok = false;
                    try {
                        if (!isLive || telemetry == null || !vin.equals(telemetry.vin) || !current.getAsBoolean()) {
                            AppLog.event(app, "ABRP", "SKIPPED stale context/cache/demo"); return;
                        }
                        storage.saveActiveTelemetry(telemetry);
                        AppLog.event(app, "ABRP", "VEHICLE_DATA ageMs=" + Math.max(0, System.currentTimeMillis() - telemetry.timestampMillis));
                        ok = AbrpClient.sendTelemetry(token, telemetry, capacity, event -> AppLog.event(app, "ABRP", event)) && current.getAsBoolean();
                        if (ok) storage.setAbrpLastSync(System.currentTimeMillis());
                        AppLog.event(app, "ABRP", ok ? "UPLOAD_OK" : "UPLOAD_FAILED_OR_CANCELLED");
                    } catch (Exception error) { AppLog.event(app, "ABRP", "UPLOAD_EXCEPTION " + error.getClass().getSimpleName()); }
                    finally { finish.accept(ok); }
                }
                @Override public void onError(String message, VehicleTelemetry fallback) {
                    AppLog.event(app, "ABRP", "FRESH_FETCH_FAILED " + message); finish.accept(false);
                }
            });
        } catch (Exception error) { AppLog.event(app, "ABRP", "START_FAILED " + error.getClass().getSimpleName()); finish.accept(false); }
    }
}
