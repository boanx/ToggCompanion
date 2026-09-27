package com.togg.companion.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class TelemetryParserTest {

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
