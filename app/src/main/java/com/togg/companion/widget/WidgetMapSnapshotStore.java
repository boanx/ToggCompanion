package com.togg.companion.widget;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import java.io.*;
/** Versioned, atomic map-only cache bound to one vehicle and coordinate pair. */
public final class WidgetMapSnapshotStore {
    private static final int MAGIC = 0x544D5032;
    private WidgetMapSnapshotStore() { }
    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "widget_map_v2.bin")); }
    public static boolean matches(String savedVin, double savedLat, double savedLon, String vin, double lat, double lon) {
        return vin != null && !vin.isEmpty() && vin.equalsIgnoreCase(savedVin)
                && Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
                && !(lat == 0 && lon == 0) && Math.abs(savedLat - lat) < 0.00001 && Math.abs(savedLon - lon) < 0.00001;
    }
    public static synchronized void save(Context context, Bitmap bitmap, String vin, double lat, double lon) throws IOException {
        if (!matches(vin, lat, lon, vin, lat, lon)) return;
        AtomicFile cache = file(context); FileOutputStream raw = cache.startWrite();
        try {
            DataOutputStream out = new DataOutputStream(raw);
            out.writeInt(MAGIC); out.writeUTF(vin); out.writeDouble(lat); out.writeDouble(lon); out.flush();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, raw)) throw new IOException("Map encoding failed");
            cache.finishWrite(raw);
        } catch (Exception error) { cache.failWrite(raw); throw new IOException("Map cache write failed", error); }
    }
    public static synchronized Bitmap load(Context context, String vin, double lat, double lon) {
        AtomicFile cache = file(context);
        if (cache.getBaseFile().length() > 4 * 1024 * 1024) return null;
        try (DataInputStream in = new DataInputStream(cache.openRead())) {
            if (in.readInt() != MAGIC || !matches(in.readUTF(), in.readDouble(), in.readDouble(), vin, lat, lon)) return null;
            return BitmapFactory.decodeStream(in);
        } catch (Exception unavailable) { return null; }
    }
}
