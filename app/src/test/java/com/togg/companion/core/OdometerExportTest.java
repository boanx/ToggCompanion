package com.togg.companion.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OdometerExportTest {
    @Test public void onlyMileageAndHonestTimesAreExported() throws Exception {
        JSONObject json = new JSONObject(OdometerExport.json(4499, 1794780000000L, 1794866400000L));
        assertEquals(3, json.length());
        assertEquals(4499L, json.getLong("odometer_km"));
        assertEquals("2026-11-16T22:00:00Z", json.getString("retrieved_at"));
        assertNotEquals(json.getString("vehicle_data_at"), json.getString("retrieved_at"));
        assertFalse(json.has("vin")); assertFalse(json.has("battery_soc")); assertFalse(json.has("myTrip"));
    }
    @Test public void unknownMileageOrTimeIsNeverInvented() throws Exception {
        for (long[] value : new long[][]{{0,1,1},{-1,1,1},{1,0,1},{1,1,0}}) {
            try { OdometerExport.json(value[0],value[1],value[2]); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void futureAnchorIsRespected() { assertEquals(9000000, OdometerExport.nextTime(9000000, 15, 1)); }
    @Test public void missedSlotsAreSkippedWithoutDrift() {
        assertEquals(3600001, OdometerExport.nextTime(1, 15, 2700002));
        assertEquals(1800001, OdometerExport.nextTime(1, 15, 900001));
    }
    @Test public void arbitraryUserIntervalAndDailyAnchorWork() {
        assertEquals(1020001, OdometerExport.nextTime(1, 17, 1));
        assertEquals(86400001, OdometerExport.nextTime(1, 1440, 86400000));
    }
    @Test public void invalidIntervalsAreRejected() {
        for (long interval : new long[]{-1,0,1,14,525601,Long.MAX_VALUE}) {
            try { OdometerExport.nextTime(1, interval, 1); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
}
