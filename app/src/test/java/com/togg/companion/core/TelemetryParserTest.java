package com.togg.companion.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class TelemetryParserTest {

    @Test
    public void testReadyToDriveFromVehicleStatus() {
        assertTrue(TelemetryParser.parse("{\"data\":{\"telemetry\":{\"vehicleStatus\":{\"vehicleStatusValueEnum\":\"READY\"}}}}", "VIN", "PLATE").isReadyToDrive);
        assertTrue(TelemetryParser.parse("{\"data\":{\"telemetry\":{\"vehicleStatus\":{\"vehicleStatusValueEnum\":\"DRIVE\"}}}}", "VIN", "PLATE").isReadyToDrive);
    }

    @Test
    public void testReadyToDriveFromPositiveSpeed() {
        assertTrue(TelemetryParser.parse("{\"data\":{\"telemetry\":{\"vehicleSpeed\":1}}}", "VIN", "PLATE").isReadyToDrive);
    }

    @Test
    public void testReadyToDriveFromBooleanFlag() {
        assertTrue(TelemetryParser.parse("{\"data\":{\"telemetry\":{\"isReadyToDrive\":true}}}", "VIN", "PLATE").isReadyToDrive);
    }

    @Test
    public void testParseTimestampIsoVariantsAndEpochs() {
        long expected = java.time.Instant.parse("2026-10-01T10:15:30Z").toEpochMilli();
        assertEquals(expected, TelemetryParser.parseTimestamp("2026-10-01T10:15:30Z", -1));
        assertEquals(expected, TelemetryParser.parseTimestamp("2026-10-01T13:15:30+03:00", -1));
        assertEquals(expected + 123, TelemetryParser.parseTimestamp("2026-10-01T10:15:30.123Z", -1));
        assertEquals(java.time.LocalDateTime.parse("2026-10-01T10:15:30")
                .atZone(java.time.ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli(),
                TelemetryParser.parseTimestamp("2026-10-01T10:15:30", -1));
        assertEquals(1727777730000L, TelemetryParser.parseTimestamp(1727777730000L, -1));
        assertEquals(1727777730000L, TelemetryParser.parseTimestamp("1727777730", -1));
    }

    @Test
    public void testVehicleUpdatedOnIsNotSyncTimestamp() {
        String json = "{\"data\":{\"telemetry\":{\"updatedOn\":\"2026-10-01T10:15:30\",\"batterySoc\":82}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "VIN", "PLATE");
        assertEquals(0L, telemetry.timestampMillis);
    }

    @Test
    public void testTurkishVehicleSyncDates() {
        long expected = java.time.LocalDateTime.of(2026, 9, 30, 15, 50)
                .atZone(java.time.ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli();
        assertEquals(expected, TelemetryParser.parseTimestamp("30 Eylül 2026 - 15:50", -1));
        assertEquals(expected, TelemetryParser.parseTimestamp("30.09.2026 15:50", -1));
        assertEquals(expected, TelemetryParser.parse("{\"data\":{\"dateFormat\":\"30 Eylül 2026 - 15:50\",\"telemetry\":{\"updatedOn\":\"2026-10-01T10:15:30Z\"}}}", "VIN", "PLATE").timestampMillis);
        assertEquals(expected, TelemetryParser.parse("{\"timestamp\":\"30.09.2026 15:50\",\"data\":{\"telemetry\":{}}}", "VIN", "PLATE").timestampMillis);
    }

    @Test
    public void testComponentProducedOnAndLocalStateChangesKeepVehicleSync() {
        String json = "{\"data\":{\"telemetry\":{\"updatedOn\":\"2026-10-01T12:00:00Z\","
                + "\"battery\":{\"producedOn\":\"2026-09-30T12:50:00Z\"}}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "VIN", "PLATE");
        long expected = java.time.Instant.parse("2026-09-30T12:50:00Z").toEpochMilli();
        assertEquals(expected, telemetry.timestampMillis);
        assertEquals(expected, telemetry.withLock(false).withTrunk(true).withReadyToDrive(true).timestampMillis);
    }

    @Test
    public void testProducedOnPrioritizedOverUpdatedOn() {
        String json = "{\"data\":{\"telemetry\":{" +
                "\"updatedOn\":\"2026-10-01T10:15:30Z\"," +
                "\"tirePressureSensors\":{\"producedOn\":\"2026-10-01T09:00:00Z\"}," +
                "\"batterySoc\":82}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "VIN", "PLATE");
        assertEquals(java.time.Instant.parse("2026-10-01T09:00:00Z").toEpochMilli(), telemetry.timestampMillis);
    }

    @Test
    public void testRootVehicleTimestampIsParsed() {
        String json = "{\"data\":{\"timestamp\":\"2026-10-01T10:15:30Z\",\"telemetry\":{\"batterySoc\":82}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "VIN", "PLATE");
        assertEquals(java.time.Instant.parse("2026-10-01T10:15:30Z").toEpochMilli(), telemetry.timestampMillis);
    }

    @Test
    public void testRootVehicleTimestampPrecedesSensorTimestamp() {
        String json = "{\"data\":{\"timestamp\":\"2026-10-01T10:15:30Z\",\"telemetry\":{" +
                "\"updatedOn\":\"2026-10-01T11:00:00Z\",\"tirePressureSensors\":{\"producedOn\":\"2026-10-01T09:00:00Z\"}}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "VIN", "PLATE");
        assertEquals(java.time.Instant.parse("2026-10-01T10:15:30Z").toEpochMilli(), telemetry.timestampMillis);
    }

    @Test
    public void testRealToggOdometerMetersConvertedToKilometers() {
        String json = "{\"data\":{\"telemetry\":{\"odometerStatus\":{" +
                "\"odometerStatusContent\":{\"odometer\":{\"odometerValue\":536384}}}}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "VIN", "PLATE");
        assertEquals(536L, telemetry.odometerKm);
        assertEquals(536.384, telemetry.myTrip.distanceKm, 0.001);
    }

    @Test
    public void testMissingVehicleTimestampIsNotInvented() {
        VehicleTelemetry telemetry = TelemetryParser.parse("{\"data\":{\"telemetry\":{\"batterySoc\":82}}}", "VIN", "PLATE");
        assertEquals(0L, telemetry.timestampMillis);
    }

    @Test
    public void testDefaultDemoSnapshot() {
        VehicleTelemetry t = TelemetryParser.createDemoSnapshot("T10F-001", "34 TOGG 10");
        assertNotNull(t);
        assertEquals("T10F-001", t.vin);
        assertEquals("34 TOGG 10", t.plate);
        assertEquals(82, t.batterySocPercent);
        assertEquals(426, t.estimatedRangeKm);
        assertEquals(94, t.lowVoltage12vPercent);
        assertFalse(t.is12vBatteryLow());
        assertFalse(t.isAnyTireLow());
        assertEquals(2.5, t.tirePressureFL, 0.01);
        assertEquals(2.6, t.tirePressureRR, 0.01);
    }

    @Test
    public void testJsonParsingStandardPayload() {
        String json = "{\n" +
                "  \"data\": {\n" +
                "    \"vin\": \"T10F-VIN-TEST\",\n" +
                "    \"plate\": \"06 TGG 99\",\n" +
                "    \"batterySoc\": 88,\n" +
                "    \"estimatedRange\": 450,\n" +
                "    \"isCharging\": true,\n" +
                "    \"chargePowerKw\": 22.0,\n" +
                "    \"lowVoltageSoc\": 96,\n" +
                "    \"cabinTemperature\": 21.0,\n" +
                "    \"targetHvacTemperature\": 20.5,\n" +
                "    \"outdoorTemperature\": 15.0,\n" +
                "    \"tirePressure\": {\n" +
                "      \"fl\": 2.4,\n" +
                "      \"fr\": 2.4,\n" +
                "      \"rl\": 2.5,\n" +
                "      \"rr\": 2.5\n" +
                "    },\n" +
                "    \"tireTemperature\": {\n" +
                "      \"fl\": 20,\n" +
                "      \"fr\": 20,\n" +
                "      \"rl\": 21,\n" +
                "      \"rr\": 21\n" +
                "    },\n" +
                "    \"odometer\": 5500,\n" +
                "    \"isLocked\": true\n" +
                "  }\n" +
                "}";

        VehicleTelemetry t = TelemetryParser.parse(json, "FALLBACK", "FALLBACK");
        assertNotNull(t);
        assertEquals("T10F-VIN-TEST", t.vin);
        assertEquals("06 TGG 99", t.plate);
        assertEquals(88, t.batterySocPercent);
        assertEquals(450, t.estimatedRangeKm);
        assertTrue(t.isCharging);
        assertEquals(22.0, t.chargePowerKw, 0.1);
        assertEquals(96, t.lowVoltage12vPercent);
        assertEquals(2.4, t.tirePressureFL, 0.01);
        assertEquals(2.5, t.tirePressureRR, 0.01);
        assertEquals(5500L, t.odometerKm);
        assertTrue(t.isLocked);
    }

    @Test
    public void testLow12vBatteryWarning() {
        VehicleTelemetry critical = new VehicleTelemetry(
                "VIN", "PLATE", 50, 200, false, 0.0,
                45, // 12V is 45% -> should warn!
                20.0, 20.0, 15.0,
                2.4, 20, 2.4, 20, 2.5, 20, 2.5, 20,
                1000, true, System.currentTimeMillis()
        );
        assertTrue("45% should trigger 12V warning", critical.is12vBatteryLow());

        VehicleTelemetry safe = new VehicleTelemetry(
                "VIN", "PLATE", 50, 200, false, 0.0,
                85, // 12V is 85% -> safe
                20.0, 20.0, 15.0,
                2.4, 20, 2.4, 20, 2.5, 20, 2.5, 20,
                1000, true, System.currentTimeMillis()
        );
        assertFalse("85% should not trigger 12V warning", safe.is12vBatteryLow());
    }

    @Test
    public void testLowTirePressureWarning() {
        VehicleTelemetry flatTire = new VehicleTelemetry(
                "VIN", "PLATE", 80, 400, false, 0.0,
                90,
                20.0, 20.0, 15.0,
                1.8, 20, // 1.8 bar -> dangerously low!
                2.5, 20, 2.5, 20, 2.5, 20,
                1000, true, System.currentTimeMillis()
        );
        assertTrue("1.8 bar should trigger TPMS warning", flatTire.isAnyTireLow());
    }

    @Test
    public void testResilientFallbackOnCorruptJson() {
        VehicleTelemetry t = TelemetryParser.parse("{invalid:json", "DEF-VIN", "DEF-PLATE");
        assertNotNull(t);
        assertEquals("DEF-VIN", t.vin);
        assertEquals("DEF-PLATE", t.plate);
    }
}
