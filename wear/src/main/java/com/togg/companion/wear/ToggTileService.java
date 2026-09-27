package com.togg.companion.wear;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.wear.tiles.ActionBuilders;
import androidx.wear.tiles.ColorBuilders;
import androidx.wear.tiles.DimensionBuilders;
import androidx.wear.tiles.LayoutElementBuilders;
import androidx.wear.tiles.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.tiles.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;

/**
 * Samsung Galaxy Watch Tile (Kutucuk / Hızlı Bakış Ekranı).
 * Appears when user swipes left on their Galaxy Watch face.
 * Displays the 4 key vehicle telemetry metrics at a glance.
 */
public class ToggTileService extends TileService {
    private static final String RESOURCES_VERSION = "1";

    public static void requestTileUpdate(Context context) {
        if (context == null) return;
        try {
            getUpdater(context).requestUpdate(ToggTileService.class);
        } catch (Exception ignored) {}
    }

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest requestParams) {
        WatchTelemetry t = WatchDataStore.getTelemetry(this);

        // Click on Tile launches the full Watch MainActivity
        ActionBuilders.Action launchAction = new ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(getPackageName())
                        .setClassName(MainActivity.class.getName())
                        .build())
                .build();

        ModifiersBuilders.Clickable tileClickable = new ModifiersBuilders.Clickable.Builder()
                .setId("open_app")
                .setOnClick(launchAction)
                .build();

        ModifiersBuilders.Modifiers tileModifiers = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(tileClickable)
                .build();

        // 1. Top Badge: Togg T10F and Last Update
        LayoutElementBuilders.Text topText = new LayoutElementBuilders.Text.Builder()
                .setText("T10F • " + t.getRelativeTimeString())
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(12))
                        .setColor(ColorBuilders.argb(0xFF00C2D6))
                        .build())
                .build();

        // 2. Center HV Battery: e.g. "%78  385 km"
        String chargingSymbol = t.isCharging ? " ⚡" : "";
        LayoutElementBuilders.Text socText = new LayoutElementBuilders.Text.Builder()
                .setText("%" + t.soc + chargingSymbol)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(34))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(ColorBuilders.argb(t.isCharging ? 0xFF00E676 : 0xFFFFFFFF))
                        .build())
                .build();

        LayoutElementBuilders.Text rangeText = new LayoutElementBuilders.Text.Builder()
                .setText(t.rangeKm + " km menzil")
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(14))
                        .setColor(ColorBuilders.argb(0xFF94A3B8))
                        .build())
                .build();

        // 3. Middle Row: 12V and Tires Badges
        int color12v = t.batt12vAlert ? 0xFFEF4444 : 0xFF10B981;
        LayoutElementBuilders.Text batt12vText = new LayoutElementBuilders.Text.Builder()
                .setText("12V: %" + t.batt12vSoc)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(12))
                        .setColor(ColorBuilders.argb(color12v))
                        .build())
                .build();

        LayoutElementBuilders.Text divider = new LayoutElementBuilders.Text.Builder()
                .setText("  |  ")
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(12))
                        .setColor(ColorBuilders.argb(0xFF64748B))
                        .build())
                .build();

        int colorTires = t.tiresNormal ? 0xFF10B981 : 0xFFEF4444;
        String tireStr = t.tiresNormal ? "Lastik: OK" : "Lastik: UYARI";
        LayoutElementBuilders.Text tiresText = new LayoutElementBuilders.Text.Builder()
                .setText(tireStr)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(12))
                        .setColor(ColorBuilders.argb(colorTires))
                        .build())
                .build();

        LayoutElementBuilders.Row statusRow = new LayoutElementBuilders.Row.Builder()
                .addContent(batt12vText)
                .addContent(divider)
                .addContent(tiresText)
                .build();

        // 4. Bottom Location Chip: e.g. "📍 Kadıköy (Araca Yürü)"
        String locStr = (t.parkAddress != null && !t.parkAddress.isEmpty()) ? t.parkAddress : "Konum Hazır";
        if (locStr.length() > 22) {
            locStr = locStr.substring(0, 20) + "..";
        }
        LayoutElementBuilders.Text locText = new LayoutElementBuilders.Text.Builder()
                .setText("📍 " + locStr)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(11))
                        .setColor(ColorBuilders.argb(0xFF3874FF))
                        .build())
                .build();

        // Root Column Layout
        LayoutElementBuilders.Column rootColumn = new LayoutElementBuilders.Column.Builder()
                .setModifiers(tileModifiers)
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(10)).build())
                .addContent(topText)
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4)).build())
                .addContent(socText)
                .addContent(rangeText)
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6)).build())
                .addContent(statusRow)
                .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8)).build())
                .addContent(locText)
                .build();

        LayoutElementBuilders.Layout layout = new LayoutElementBuilders.Layout.Builder()
                .setRoot(rootColumn)
                .build();

        TimelineBuilders.TimelineEntry entry = new TimelineBuilders.TimelineEntry.Builder()
                .setLayout(layout)
                .build();

        TimelineBuilders.Timeline timeline = new TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(entry)
                .build();

        TileBuilders.Tile tile = new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTimeline(timeline)
                .setFreshnessIntervalMillis(60 * 1000) // update every 1 min
                .build();

        return Futures.immediateFuture(tile);
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(@NonNull RequestBuilders.ResourcesRequest requestParams) {
        ResourceBuilders.Resources resources = new ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build();
        return Futures.immediateFuture(resources);
    }
}
