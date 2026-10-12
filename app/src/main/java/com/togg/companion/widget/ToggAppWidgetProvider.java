package com.togg.companion.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.SizeF;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.togg.companion.R;
import com.togg.companion.core.TelemetryParser;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.TelemetryDatabaseHelper;
import com.togg.companion.data.remote.TvaApiClient;
import com.togg.companion.ui.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

public final class ToggAppWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.togg.companion.widget.REFRESH";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        updateWidget(context, manager, id, options);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !ACTION_REFRESH.equals(intent.getAction())) return;

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ToggAppWidgetProvider.class));
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_togg);
            views.setTextViewText(R.id.widget_updated, "Yenileniyor... ");
            manager.partiallyUpdateAppWidget(id, views);
        }

        final PendingResult result = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                SecureTokenStorage storage = new SecureTokenStorage(appContext);
                TelemetryDatabaseHelper dbHelper = new TelemetryDatabaseHelper(appContext);
                TvaApiClient apiClient = new TvaApiClient(storage);
                apiClient.fetchTelemetry(new TvaApiClient.TelemetryCallback() {
                    private void persistAndUpdate(VehicleTelemetry telemetry) {
                        try {
                            if (telemetry != null) {
                                dbHelper.insert(telemetry);
                                storage.saveActiveTelemetry(telemetry);
                                if (telemetry.timestampMillis > 0) storage.setLastSync(telemetry.timestampMillis);
                            }
                            storage.setLastRefresh(System.currentTimeMillis());
                            updateAllWidgets(appContext);
                        } finally {
                            result.finish();
                        }
                    }

                    @Override public void onSuccess(VehicleTelemetry telemetry, boolean isLive) {
                        persistAndUpdate(telemetry);
                    }

                    @Override public void onError(String message, VehicleTelemetry fallback) {
                        persistAndUpdate(fallback);
                    }
                });
            } catch (Exception ignored) {
                try { updateAllWidgets(appContext); } finally { result.finish(); }
            }
        }, "ToggWidgetRefresh").start();
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ToggAppWidgetProvider.class));
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id) {
        updateWidget(context, manager, id, null);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id, Bundle suppliedOptions) {
        Bundle options = suppliedOptions != null ? suppliedOptions : manager.getAppWidgetOptions(id);
        int minWidth = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
        int minHeight = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) : 0;
        boolean expanded = minHeight >= 70;
        int level = minWidth >= 310 || minWidth == 0 ? 0 : minWidth >= 240 ? 1 : 2;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_togg);
        SecureTokenStorage storage = new SecureTokenStorage(context);
        VehicleTelemetry t = storage.getActiveTelemetry(context);
        if (t == null) t = new TelemetryDatabaseHelper(context).getLatest();
        if (t == null) t = TelemetryParser.createDemoSnapshot(storage.getVin(), storage.getPlate());
        if (storage.hasSavedLocation()) {
            t = t.withLocation(storage.getLastLat(), storage.getLastLon(), storage.getLastAddress());
        }

        views.setTextViewText(R.id.widget_title, storage.getVehicleName());
        views.setTextViewText(R.id.widget_plate, t.plate != null && !t.plate.isEmpty() ? t.plate : storage.getPlate());
        views.setImageViewBitmap(R.id.widget_hv_battery_view, createHvBatteryBitmap(t.batterySocPercent));
        views.setImageViewBitmap(R.id.widget_range_view, createRoadRangeBitmap(t.estimatedRangeKm, t.isCharging));
        views.setTextViewText(R.id.widget_range, t.estimatedRangeKm + " km");
        views.setViewVisibility(R.id.widget_charging, t.isCharging ? View.VISIBLE : View.GONE);
        views.setImageViewBitmap(R.id.widget_12v_battery_view, create12vCarBatteryBitmap(t.lowVoltage12vPercent));
        setTirePressure(views, R.id.widget_tire_fl, t.getTirePressurePsiFL());
        setTirePressure(views, R.id.widget_tire_fr, t.getTirePressurePsiFR());
        setTirePressure(views, R.id.widget_tire_rl, t.getTirePressurePsiRL());
        setTirePressure(views, R.id.widget_tire_rr, t.getTirePressurePsiRR());

        boolean telemetryHasLocation = Double.isFinite(t.latitude) && Double.isFinite(t.longitude)
                && t.latitude >= -90.0 && t.latitude <= 90.0
                && t.longitude >= -180.0 && t.longitude <= 180.0
                && !(t.latitude == 0.0 && t.longitude == 0.0);
        double lat = telemetryHasLocation ? t.latitude : storage.getLastLat();
        double lon = telemetryHasLocation ? t.longitude : storage.getLastLon();
        boolean hasLocation = Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0
                && !(lat == 0.0 && lon == 0.0);
        String savedAddress = storage.getLastAddress();
        boolean oldDemoLocation = lat == 39.925054 && lon == 32.834755
                && (savedAddress == null || savedAddress.toLowerCase(Locale.ROOT).contains("belirleniyor"));
        if (oldDemoLocation) { lat = 0.0; lon = 0.0; hasLocation = false; }
        String addr = t.parkAddress != null && !t.parkAddress.isEmpty() && !t.parkAddress.contains("belirleniyor")
                ? t.parkAddress : storage.getLastAddress();
        if (addr == null || addr.trim().isEmpty() || addr.toLowerCase(Locale.ROOT).contains("belirleniyor")) {
            addr = savedAddress != null && !savedAddress.trim().isEmpty() && !savedAddress.toLowerCase(Locale.ROOT).contains("belirleniyor")
                    ? savedAddress : lat != 0.0 && lon != 0.0 ? String.format(Locale.US, "GPS: %.4f, %.4f", lat, lon) : "Konum bulunamadı";
        }
        views.setTextViewText(R.id.widget_address, addr);
        views.setTextViewText(R.id.widget_coords, hasLocation
                ? String.format(Locale.US, "%.5f, %.5f", lat, lon) : "Konum yok");
        try {
            long vehicleSync = t.timestampMillis > 0 ? t.timestampMillis : storage.getLastSync();
            String mapTime = vehicleSync > 0
                    ? new SimpleDateFormat("d MMMM yyyy - HH:mm", new Locale("tr", "TR")).format(new Date(vehicleSync)) : "--:--";
            views.setImageViewBitmap(R.id.widget_map_view, createMapPreviewBitmap(context, lat, lon, mapTime));
        } catch (Throwable th) {
            views.setImageViewResource(R.id.widget_map_view, android.R.color.transparent);
        }
        Intent mapIntent;
        if (hasLocation) {
            Uri navigationUri = Uri.parse("google.navigation:q=" + lat + "," + lon + "&mode=d");
            mapIntent = new Intent(Intent.ACTION_VIEW, navigationUri);
            if (mapIntent.resolveActivity(context.getPackageManager()) == null) {
                mapIntent.setData(Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(Togg T10F Park Yeri)"));
            }
        } else {
            mapIntent = new Intent(context, com.togg.companion.ui.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        PendingIntent mapPending = PendingIntent.getActivity(context, id + 20000, mapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_location_container, mapPending);
        views.setOnClickPendingIntent(R.id.widget_map_view, mapPending);

        long lastRef = storage.getLastRefresh();
        if (lastRef <= 0) lastRef = t.timestampMillis;
        String timeStr = lastRef > 0
                ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(lastRef)) : "--:--";
        views.setTextViewText(R.id.widget_updated, "Son Veri: " + timeStr);
        if (level == 2) {
            views.setViewVisibility(R.id.widget_charge_container, View.VISIBLE);
            views.setViewVisibility(R.id.widget_12v_battery_view, View.GONE);
            views.setViewVisibility(R.id.widget_tires_cross, View.GONE);
            views.setViewVisibility(R.id.widget_address_container, View.GONE);
        } else if (level == 1) {
            views.setViewVisibility(R.id.widget_charge_container, View.VISIBLE);
            views.setViewVisibility(R.id.widget_12v_battery_view, View.GONE);
            views.setViewVisibility(R.id.widget_tires_cross, View.VISIBLE);
            views.setViewVisibility(R.id.widget_address_container, View.VISIBLE);
            views.setTextViewTextSize(R.id.widget_location_title, TypedValue.COMPLEX_UNIT_SP, 9.5f);
            views.setTextViewTextSize(R.id.widget_address, TypedValue.COMPLEX_UNIT_SP, 9.5f);
            views.setTextViewTextSize(R.id.widget_coords, TypedValue.COMPLEX_UNIT_SP, 8.5f);
        } else {
            views.setViewVisibility(R.id.widget_charge_container, View.VISIBLE);
            views.setViewVisibility(R.id.widget_12v_battery_view, View.VISIBLE);
            views.setViewVisibility(R.id.widget_tires_cross, View.VISIBLE);
            views.setViewVisibility(R.id.widget_address_container, View.VISIBLE);
            views.setTextViewTextSize(R.id.widget_location_title, TypedValue.COMPLEX_UNIT_SP, 11.5f);
            views.setTextViewTextSize(R.id.widget_address, TypedValue.COMPLEX_UNIT_SP, 11.5f);
            views.setTextViewTextSize(R.id.widget_coords, TypedValue.COMPLEX_UNIT_SP, 10.0f);
        }
        views.setViewVisibility(R.id.widget_expanded_section, expanded ? View.VISIBLE : View.GONE);

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openPending);
        Intent refresh = new Intent(context, ToggAppWidgetProvider.class).setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, id + 10000, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> variants = new HashMap<>();
            variants.put(new SizeF(170f, 70f), viewsForLevel(context, views, 2));
            variants.put(new SizeF(240f, 70f), viewsForLevel(context, views, 1));
            variants.put(new SizeF(310f, 70f), viewsForLevel(context, views, 0));
            manager.updateAppWidget(id, new RemoteViews(variants));
        } else manager.updateAppWidget(id, views);
    }

    private static RemoteViews viewsForLevel(Context context, RemoteViews source, int level) {
        RemoteViews variant = new RemoteViews(source);
        variant.setViewVisibility(R.id.widget_charge_container, View.VISIBLE);
        variant.setViewVisibility(R.id.widget_hv_battery_view, View.VISIBLE);
        variant.setViewVisibility(R.id.widget_range_view, View.VISIBLE);
        variant.setViewVisibility(R.id.widget_12v_battery_view, level == 0 ? View.VISIBLE : View.GONE);
        variant.setViewVisibility(R.id.widget_tires_cross, level == 0 || level == 1 ? View.VISIBLE : View.GONE);
        variant.setViewVisibility(R.id.widget_address_container, level == 0 || level == 1 ? View.VISIBLE : View.GONE);
        variant.setViewVisibility(R.id.widget_map_view, View.VISIBLE);
        variant.setViewVisibility(R.id.widget_expanded_section, View.VISIBLE);
        variant.setTextViewTextSize(R.id.widget_location_title, TypedValue.COMPLEX_UNIT_SP, level == 1 ? 9.5f : 11.5f);
        variant.setTextViewTextSize(R.id.widget_address, TypedValue.COMPLEX_UNIT_SP, level == 1 ? 9.5f : 11.5f);
        variant.setTextViewTextSize(R.id.widget_coords, TypedValue.COMPLEX_UNIT_SP, level == 1 ? 8.5f : 10f);
        if (level == 2) {
            variant.setViewVisibility(R.id.widget_expanded_section, View.VISIBLE);
        }
        return variant;
    }


    private static int batteryColor(int percent) {
        if (percent < 20) return 0xFFE6AFB4;
        if (percent < 50) return 0xFFE5CAA2;
        return 0xFFA8DCC8;
    }

    private static int battery12vColor(int percent) {
        if (percent < 60) return 0xFFE6AFB4;
        if (percent < 80) return 0xFFE5CAA2;
        return 0xFFC6B6E5;
    }

    private static Bitmap createHvBatteryBitmap(int percent) {
        percent = Math.max(0, Math.min(100, percent));
        Bitmap bitmap = Bitmap.createBitmap(160, 74, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int color = batteryColor(percent);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(color);
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG); outline.setColor(color); outline.setStyle(Paint.Style.STROKE); outline.setStrokeWidth(4f);
        canvas.drawRoundRect(new RectF(5, 5, 145, 67), 10, 10, outline);
        canvas.drawRoundRect(new RectF(147, 24, 157, 48), 3, 3, fill);
        if (percent > 0) canvas.drawRoundRect(new RectF(11, 11, 11 + 130f * percent / 100f, 61), 6, 6, fill);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG); text.setColor(Color.WHITE);
        text.setTextSize(29f); text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER); text.setShadowLayer(2f, 0, 1, Color.BLACK);
        float bodyCenterX = (5f + 145f) / 2f;
        float bodyCenterY = (5f + 67f) / 2f;
        Paint.FontMetrics fm = text.getFontMetrics();
        float textY = bodyCenterY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("%" + percent, bodyCenterX, textY, text);
        return bitmap;
    }

    private static Bitmap create12vCarBatteryBitmap(int percent) {
        percent = Math.max(0, Math.min(100, percent));
        Bitmap bitmap = Bitmap.createBitmap(160, 72, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int color = battery12vColor(percent);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(color);
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG); outline.setColor(color); outline.setStyle(Paint.Style.STROKE); outline.setStrokeWidth(3f);
        canvas.drawRoundRect(new RectF(8, 14, 152, 68), 7f, 7f, outline);
        canvas.drawRoundRect(new RectF(22, 3, 48, 15), 2f, 2f, fill);
        canvas.drawRoundRect(new RectF(112, 3, 138, 15), 2f, 2f, fill);
        if (percent > 0) canvas.drawRoundRect(new RectF(12, 18, 12 + 136f * percent / 100f, 64), 4f, 4f, fill);
        Paint sign = new Paint(Paint.ANTI_ALIAS_FLAG); sign.setColor(Color.WHITE); sign.setStrokeWidth(3f); sign.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(26, 28, 44, 28, sign);
        canvas.drawLine(116, 28, 134, 28, sign);
        canvas.drawLine(125, 20, 125, 36, sign);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG); text.setColor(Color.WHITE);
        text.setTextSize(23f); text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER); text.setShadowLayer(2f, 0, 1, Color.BLACK);
        float bodyCenterX = (8f + 152f) / 2f;
        float bodyCenterY = (14f + 68f) / 2f;
        Paint.FontMetrics fm = text.getFontMetrics();
        float textY = bodyCenterY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("%" + percent, bodyCenterX, textY, text);
        return bitmap;
    }

    private static Bitmap createRoadRangeBitmap(int rangeKm, boolean isCharging) {
        Bitmap bitmap = Bitmap.createBitmap(160, 74, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint asphaltCard = new Paint(Paint.ANTI_ALIAS_FLAG); asphaltCard.setColor(0xFF181818);
        canvas.drawRoundRect(new RectF(5, 5, 155, 67), 10f, 10f, asphaltCard);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG); border.setColor(0x55BEBEBE); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(2f);
        canvas.drawRoundRect(new RectF(5, 5, 155, 67), 10f, 10f, border);

        Path road = new Path();
        road.moveTo(34, 67); road.lineTo(54, 5); road.lineTo(106, 5); road.lineTo(126, 67); road.close();
        Paint roadFill = new Paint(Paint.ANTI_ALIAS_FLAG); roadFill.setColor(0xFF232323);
        canvas.drawPath(road, roadFill);

        Paint shoulders = new Paint(Paint.ANTI_ALIAS_FLAG); shoulders.setColor(0x88BEBEBE); shoulders.setStyle(Paint.Style.STROKE); shoulders.setStrokeWidth(2.5f);
        canvas.drawLine(34, 67, 54, 5, shoulders);
        canvas.drawLine(126, 67, 106, 5, shoulders);

        Paint centerLines = new Paint(Paint.ANTI_ALIAS_FLAG); centerLines.setColor(0xFFD3D3D3); centerLines.setStyle(Paint.Style.STROKE); centerLines.setStrokeWidth(2.5f); centerLines.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(80, 10, 80, 18, centerLines);
        canvas.drawLine(80, 26, 80, 36, centerLines);
        canvas.drawLine(80, 44, 80, 56, centerLines);

        RectF plate = new RectF(16, 17, 144, 55);
        Paint plateFill = new Paint(Paint.ANTI_ALIAS_FLAG); plateFill.setColor(0xEE101010);
        canvas.drawRoundRect(plate, 8f, 8f, plateFill);
        Paint plateStroke = new Paint(Paint.ANTI_ALIAS_FLAG); plateStroke.setColor(isCharging ? 0xFFA8DCC8 : 0xFFABCDEF); plateStroke.setStyle(Paint.Style.STROKE); plateStroke.setStrokeWidth(1.5f);
        canvas.drawRoundRect(plate, 8f, 8f, plateStroke);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20f);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(2f, 0, 1, Color.BLACK);
        Paint.FontMetrics pfm = textPaint.getFontMetrics();
        float pTextY = plate.centerY() - (pfm.ascent + pfm.descent) / 2f;
        String label = (isCharging ? "ϟ " : " ") + rangeKm + " km";
        canvas.drawText(label, plate.centerX(), pTextY, textPaint);
        return bitmap;
    }

    private static void setTirePressure(RemoteViews views, int viewId, double pressurePsi) {
        views.setTextViewText(viewId, (pressurePsi < 32.0 ? "! " : "") + String.format(Locale.US, "%.1f", pressurePsi));
        views.setTextColor(viewId, pressurePsi < 32.0 ? 0xFFE6AFB4 : 0xFFFFFFFF);
    }

    private static Bitmap createMapPreviewBitmap(Context context, double lat, double lon, String timeStr) {
        Bitmap bitmap = Bitmap.createBitmap(280, 140, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        File snapshotFile = new File(context.getFilesDir(), "widget_map_snapshot.png");
        boolean hasSnapshot = false;
        if (snapshotFile.exists() && snapshotFile.length() > 500) {
            try {
                Bitmap snap = BitmapFactory.decodeFile(snapshotFile.getAbsolutePath());
                if (snap != null) {
                    Rect src = new Rect(0, 0, snap.getWidth(), snap.getHeight());
                    Rect dst = new Rect(0, 0, 280, 140);
                    Paint snapshotPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
                    android.graphics.ColorMatrix grayMap = new android.graphics.ColorMatrix();
                    grayMap.setSaturation(0f);
                    snapshotPaint.setColorFilter(new android.graphics.ColorMatrixColorFilter(grayMap));
                    canvas.drawBitmap(snap, src, dst, snapshotPaint);
                    Paint scrim = new Paint(); scrim.setColor(0x33000000);
                    canvas.drawRect(0, 0, 280, 140, scrim);
                    hasSnapshot = true;
                    snap.recycle();
                }
            } catch (Throwable ignored) {}
        }
        if (!hasSnapshot) {
            drawRadarFallback(canvas, lat, lon);
        }
        drawMapHud(canvas, lat, lon, timeStr);
        return bitmap;
    }

    private static void drawRadarFallback(Canvas canvas, double lat, double lon) {
        canvas.drawColor(0xFF151515);
        Paint radar = new Paint(Paint.ANTI_ALIAS_FLAG);
        radar.setColor(0x55BEBEBE); radar.setStyle(Paint.Style.STROKE); radar.setStrokeWidth(1f);
        for (int r = 20; r <= 60; r += 20) canvas.drawCircle(140, 70, r, radar);
        canvas.drawLine(140, 8, 140, 132, radar);
        canvas.drawLine(8, 70, 272, 70, radar);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE); label.setTextSize(10f); label.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(" Harita önizlemesi için uygulamayı açın", 140f, 132f, label);
    }

    private static void drawMapHud(Canvas canvas, double lat, double lon, String syncDateStr) {
        float cx = 140f, cy = 70f;
        Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
        time.setColor(Color.WHITE);
        time.setTextSize(8.5f);
        time.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        time.setTextAlign(Paint.Align.CENTER);
        String label = syncDateStr;
        float textWidth = time.measureText(label);
        float right = 274f;
        float left = Math.max(6f, right - textWidth - 14f);
        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setColor(0xCC0F0F0F);
        canvas.drawRoundRect(new RectF(left, 6f, right, 24f), 7f, 7f, badge);
        canvas.drawText(label, (left + right) / 2f, 18.5f, time);
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG); halo.setColor(0x22ABCDEF); canvas.drawCircle(cx, cy, 29f, halo);
        Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG); pulse.setStyle(Paint.Style.STROKE); pulse.setStrokeWidth(2f);
        pulse.setColor(0x66ABCDEF); canvas.drawCircle(cx, cy, 24f, pulse);
        pulse.setColor(0xAAABCDEF); canvas.drawCircle(cx, cy, 15f, pulse);
        Paint pin = new Paint(Paint.ANTI_ALIAS_FLAG); pin.setColor(0xFFABCDEF); pin.setShadowLayer(5f, 0, 1, 0xAAABCDEF);
        canvas.drawCircle(cx, cy, 7f, pin);
        Paint center = new Paint(Paint.ANTI_ALIAS_FLAG); center.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(cx - 3.5f, cy - 5.5f, cx + 3.5f, cy + 5.5f), 2f, 2f, center);
    }
}
