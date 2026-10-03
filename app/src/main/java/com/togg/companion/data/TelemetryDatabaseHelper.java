package com.togg.companion.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.togg.companion.core.VehicleTelemetry;
import com.togg.companion.widget.ToggAppWidgetProvider;

import java.util.ArrayList;
import java.util.List;
import android.content.ContentValues;

/**
 * Local SQLite storage for vehicle telemetry history.
 * Keeps telemetry safe and private on the user's phone.
 */
public final class TelemetryDatabaseHelper extends SQLiteOpenHelper {
    private final Context appContext;
    private static final String DATABASE_NAME = "togg_telemetry.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_HISTORY = "telemetry_history";
    public static final String COL_ID = "id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_VIN = "vin";
    public static final String COL_PLATE = "plate";
    public static final String COL_BATTERY_SOC = "battery_soc";
    public static final String COL_RANGE_KM = "range_km";
    public static final String COL_IS_CHARGING = "is_charging";
    public static final String COL_CHARGE_POWER = "charge_power";
    public static final String COL_LOW_VOLTAGE = "low_voltage_soc";
    public static final String COL_CABIN_TEMP = "cabin_temp";
    public static final String COL_TARGET_HVAC = "target_hvac";
    public static final String COL_OUTDOOR_TEMP = "outdoor_temp";
    public static final String COL_TP_FL = "tp_fl";
    public static final String COL_TT_FL = "tt_fl";
    public static final String COL_TP_FR = "tp_fr";
    public static final String COL_TT_FR = "tt_fr";
    public static final String COL_TP_RL = "tp_rl";
    public static final String COL_TT_RL = "tt_rl";
    public static final String COL_TP_RR = "tp_rr";
    public static final String COL_TT_RR = "tt_rr";
    public static final String COL_ODOMETER = "odometer";
    public static final String COL_IS_LOCKED = "is_locked";
    public static final String COL_LATITUDE = "latitude";
    public static final String COL_LONGITUDE = "longitude";
    public static final String COL_PARK_ADDRESS = "park_address";
    public static final String COL_TRUNK_OPEN = "trunk_open";
    public static final String COL_DRIVE_MODE = "drive_mode";
    public static final String COL_HEADLIGHTS_ON = "headlights_on";
    public static final String COL_IS_READY_TO_DRIVE = "is_ready_to_drive";

    public TelemetryDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createSql = "CREATE TABLE " + TABLE_HISTORY + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_VIN + " TEXT, "
                + COL_PLATE + " TEXT, "
                + COL_BATTERY_SOC + " INTEGER, "
                + COL_RANGE_KM + " INTEGER, "
                + COL_IS_CHARGING + " INTEGER, "
                + COL_CHARGE_POWER + " REAL, "
                + COL_LOW_VOLTAGE + " INTEGER, "
                + COL_CABIN_TEMP + " REAL, "
                + COL_TARGET_HVAC + " REAL, "
                + COL_OUTDOOR_TEMP + " REAL, "
                + COL_TP_FL + " REAL, "
                + COL_TT_FL + " INTEGER, "
                + COL_TP_FR + " REAL, "
                + COL_TT_FR + " INTEGER, "
                + COL_TP_RL + " REAL, "
                + COL_TT_RL + " INTEGER, "
                + COL_TP_RR + " REAL, "
                + COL_TT_RR + " INTEGER, "
                + COL_ODOMETER + " INTEGER, "
                + COL_IS_LOCKED + " INTEGER, "
                + COL_LATITUDE + " REAL, "
                + COL_LONGITUDE + " REAL, "
                + COL_PARK_ADDRESS + " TEXT, "
                + COL_TRUNK_OPEN + " INTEGER, "
                + COL_DRIVE_MODE + " TEXT, "
                + COL_HEADLIGHTS_ON + " INTEGER, "
                + COL_IS_READY_TO_DRIVE + " INTEGER DEFAULT 0"
                + ");";
        db.execSQL(createSql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_LATITUDE + " REAL DEFAULT 0.0");
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_LONGITUDE + " REAL DEFAULT 0.0");
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_PARK_ADDRESS + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_TRUNK_OPEN + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_DRIVE_MODE + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_HEADLIGHTS_ON + " INTEGER DEFAULT 0");
            } catch (Exception e) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
                onCreate(db);
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN " + COL_IS_READY_TO_DRIVE + " INTEGER DEFAULT 0");
            } catch (Exception ignored) {
                // The table may have been recreated by the legacy version-1 fallback above.
            }
        }
    }

    public synchronized long insert(VehicleTelemetry t) {
        if (t == null) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, t.timestampMillis > 0 ? t.timestampMillis : System.currentTimeMillis());
        cv.put(COL_VIN, t.vin);
        cv.put(COL_PLATE, t.plate);
        cv.put(COL_BATTERY_SOC, t.batterySocPercent);
        cv.put(COL_RANGE_KM, t.estimatedRangeKm);
        cv.put(COL_IS_CHARGING, t.isCharging ? 1 : 0);
        cv.put(COL_CHARGE_POWER, t.chargePowerKw);
        cv.put(COL_LOW_VOLTAGE, t.lowVoltage12vPercent);
        cv.put(COL_CABIN_TEMP, t.cabinTempCelsius);
        cv.put(COL_TARGET_HVAC, t.targetHvacCelsius);
        cv.put(COL_OUTDOOR_TEMP, t.outdoorTempCelsius);
        cv.put(COL_TP_FL, t.tirePressureFL);
        cv.put(COL_TT_FL, t.tireTempFL);
        cv.put(COL_TP_FR, t.tirePressureFR);
        cv.put(COL_TT_FR, t.tireTempFR);
        cv.put(COL_TP_RL, t.tirePressureRL);
        cv.put(COL_TT_RL, t.tireTempRL);
        cv.put(COL_TP_RR, t.tirePressureRR);
        cv.put(COL_TT_RR, t.tireTempRR);
        cv.put(COL_ODOMETER, t.odometerKm);
        cv.put(COL_IS_LOCKED, t.isLocked ? 1 : 0);
        cv.put(COL_LATITUDE, t.latitude);
        cv.put(COL_LONGITUDE, t.longitude);
        cv.put(COL_PARK_ADDRESS, t.parkAddress);
        cv.put(COL_TRUNK_OPEN, t.trunkOpen ? 1 : 0);
        cv.put(COL_DRIVE_MODE, t.driveMode);
        cv.put(COL_HEADLIGHTS_ON, t.headlightsOn ? 1 : 0);
        cv.put(COL_IS_READY_TO_DRIVE, t.isReadyToDrive ? 1 : 0);
        long rowId = db.insert(TABLE_HISTORY, null, cv);
        if (rowId != -1) {
            db.execSQL("DELETE FROM telemetry_history WHERE id NOT IN (SELECT id FROM telemetry_history ORDER BY id DESC LIMIT 100)");
        }
        ToggAppWidgetProvider.updateAllWidgets(appContext);
        return rowId;
    }

    public synchronized void clearHistory() {
        getWritableDatabase().delete(TABLE_HISTORY, null, null);
        ToggAppWidgetProvider.updateAllWidgets(appContext);
    }

    public synchronized VehicleTelemetry getLatest() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_HISTORY, null, null, null, null, null, COL_ID + " DESC", "1");
        if (c != null && c.moveToFirst()) {
            VehicleTelemetry t = fromCursor(c);
            c.close();
            return t;
        }
        if (c != null) c.close();
        return null;
    }

    public synchronized List<VehicleTelemetry> getHistory(int limit) {
        List<VehicleTelemetry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_HISTORY, null, null, null, null, null, COL_TIMESTAMP + " DESC", String.valueOf(limit));
        if (c != null && c.moveToFirst()) {
            do {
                list.add(fromCursor(c));
            } while (c.moveToNext());
            c.close();
        }
        if (c != null) c.close();
        return list;
    }

    private VehicleTelemetry fromCursor(Cursor c) {
        int latIdx = c.getColumnIndex(COL_LATITUDE);
        double lat = (latIdx != -1 && !c.isNull(latIdx)) ? c.getDouble(latIdx) : 0.0;
        int lonIdx = c.getColumnIndex(COL_LONGITUDE);
        double lon = (lonIdx != -1 && !c.isNull(lonIdx)) ? c.getDouble(lonIdx) : 0.0;
        int addrIdx = c.getColumnIndex(COL_PARK_ADDRESS);
        String addr = (addrIdx != -1 && !c.isNull(addrIdx)) ? c.getString(addrIdx) : null;
        int trunkIdx = c.getColumnIndex(COL_TRUNK_OPEN);
        boolean trunk = (trunkIdx != -1 && !c.isNull(trunkIdx)) && (c.getInt(trunkIdx) == 1);
        int modeIdx = c.getColumnIndex(COL_DRIVE_MODE);
        String mode = (modeIdx != -1 && !c.isNull(modeIdx)) ? c.getString(modeIdx) : "Comfort";
        int headIdx = c.getColumnIndex(COL_HEADLIGHTS_ON);
        boolean headlights = (headIdx != -1 && !c.isNull(headIdx)) && (c.getInt(headIdx) == 1);
        int readyIdx = c.getColumnIndex(COL_IS_READY_TO_DRIVE);
        boolean ready = readyIdx != -1 && !c.isNull(readyIdx) && c.getInt(readyIdx) == 1;

        return new VehicleTelemetry(
                c.getString(c.getColumnIndexOrThrow(COL_VIN)),
                c.getString(c.getColumnIndexOrThrow(COL_PLATE)),
                c.getInt(c.getColumnIndexOrThrow(COL_BATTERY_SOC)),
                c.getInt(c.getColumnIndexOrThrow(COL_RANGE_KM)),
                c.getInt(c.getColumnIndexOrThrow(COL_IS_CHARGING)) == 1,
                c.getDouble(c.getColumnIndexOrThrow(COL_CHARGE_POWER)),
                c.getInt(c.getColumnIndexOrThrow(COL_LOW_VOLTAGE)),
                c.getDouble(c.getColumnIndexOrThrow(COL_CABIN_TEMP)),
                c.getDouble(c.getColumnIndexOrThrow(COL_TARGET_HVAC)),
                c.getDouble(c.getColumnIndexOrThrow(COL_OUTDOOR_TEMP)),
                c.getDouble(c.getColumnIndexOrThrow(COL_TP_FL)),
                c.getInt(c.getColumnIndexOrThrow(COL_TT_FL)),
                c.getDouble(c.getColumnIndexOrThrow(COL_TP_FR)),
                c.getInt(c.getColumnIndexOrThrow(COL_TT_FR)),
                c.getDouble(c.getColumnIndexOrThrow(COL_TP_RL)),
                c.getInt(c.getColumnIndexOrThrow(COL_TT_RL)),
                c.getDouble(c.getColumnIndexOrThrow(COL_TP_RR)),
                c.getInt(c.getColumnIndexOrThrow(COL_TT_RR)),
                c.getLong(c.getColumnIndexOrThrow(COL_ODOMETER)),
                c.getInt(c.getColumnIndexOrThrow(COL_IS_LOCKED)) == 1,
                lat,
                lon,
                addr,
                trunk,
                mode,
                headlights, ready,
                c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP))
        );
    }
}
