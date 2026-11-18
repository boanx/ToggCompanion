package com.togg.companion.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChargingInfoTest {
    @Test public void originalTrumoreChargeEnumIsChargingWithoutInventingPower() {
        VehicleTelemetry t = parse("\"obcState\":{\"content\":{\"chargeMode\":{\"chargeModeEnumValue\":\"CHRGMODE_CHARGE\"}}}");
        assertTrue(t.isCharging); assertEquals("Şarj Oluyor",t.chargeMode); assertEquals(-1,t.chargePowerKw,0);
        assertEquals("Şarj oluyor",ChargingInfo.statusText(t.isCharging,t.chargePowerKw));
        assertFalse(parse("\"chargeMode\":\"CHRGMODE_STANDBY\"").isCharging);
    }
    @Test public void textualChargingStatusDoesNotBecomeFalse() {
        for (String state : new String[]{"charging","CHARGE","CHRGMODE_CHARGE","true","1"}) {
            VehicleTelemetry t = parse("\"chargingStatus\":\""+state+"\",\"chargePowerKw\":80");
            assertTrue(state,t.isCharging); assertEquals(80,t.chargePowerKw,0);
        }
    }
    @Test public void unknownChargingStatusDoesNotOverrideChargeMode() {
        for (String state : new String[]{"SNA","unknown","","42"})
            assertTrue(parse("\"chargeMode\":\"CHRGMODE_CHARGE\",\"chargingStatus\":\""+state+"\"").isCharging);
        assertTrue(parse("\"chargeMode\":\"CHRGMODE_CHARGE\",\"chargingStatus\":null").isCharging);
        assertTrue(parse("\"chargeMode\":\"CHRGMODE_CHARGE\",\"chargingStatus\":42").isCharging);
    }
    @Test public void explicitNegativeStateStillOverridesPositiveMode() {
        for (String state : new String[]{"false","NotCharging","NOT_CHARGING","CHRGMODE_STANDBY"})
            assertFalse(parse("\"chargeMode\":\"CHRGMODE_CHARGE\",\"chargingStatus\":\""+state+"\"").isCharging);
        assertFalse(parse("\"chargeMode\":\"CHRGMODE_CHARGE\",\"isCharging\":false").isCharging);
    }
    @Test public void wrappedStatusAndHistoryAreSeparated() {
        assertTrue(parse("\"chargingStatus\":{\"value\":\"CHARGING\"}").isCharging);
        assertTrue(parse("\"chargeMode\":\"CHRGMODE_CHARGE\",\"lastTripInfo\":{\"isCharging\":false}").isCharging);
        assertFalse(parse("\"myTrip\":{\"isCharging\":true},\"chargingStation\":{\"charging\":true}").isCharging);
    }
    @Test public void statusOnlyShowsMeasuredPower() {
        assertEquals("Şarj oluyor", ChargingInfo.statusText(true, -1));
        assertEquals("Şarj oluyor", ChargingInfo.statusText(true, Double.NaN));
        assertEquals("Şarj oluyor- 80kW", ChargingInfo.statusText(true, 80));
        assertEquals("Şarj oluyor- 0kW", ChargingInfo.statusText(true, 0));
        assertEquals("Şarj olmuyor", ChargingInfo.statusText(false, 80));
    }
    @Test public void diagnosticDistinguishesMissingInvalidAndReportedWithoutSecrets() {
        String missing = ChargingInfo.diagnostic("{\"telemetry\":{\"isCharging\":true,\"power\":11}}", true, 123);
        assertTrue(missing.contains("supportedFields=0")); assertTrue(missing.contains("reportedKw=UNAVAILABLE"));
        String invalid = ChargingInfo.diagnostic("{\"telemetry\":{\"chargingPower\":\"SNA\"}}", true, 123);
        assertTrue(invalid.contains("supportedFields=1 invalidFields=1"));
        String known = ChargingInfo.diagnostic("{\"data\":{\"vin\":\"PRIVATE_VIN\",\"token\":\"PRIVATE_TOKEN\",\"telemetry\":{\"obcState\":{\"content\":{\"chargePowerKw\":80}}}}}", true, 123);
        assertTrue(known.contains("selectedField=chargePowerKw reportedKw=80.0 usedForDisplay=true"));
        assertFalse(known.contains("PRIVATE"));
    }
    private VehicleTelemetry parse(String fields) {
        VehicleTelemetry value = TelemetryParser.parse("{\"telemetry\":{" + fields + "}}", "TEST", "TEST");
        assertNotNull(value); return value;
    }
    @Test public void missingAcPowerDoesNotInventEleven() {
        VehicleTelemetry t = parse("\"obcState\":{\"content\":{\"chargeMode\":{\"chargeModeEnumValue\":\"AC\"}}}");
        assertTrue(t.isCharging); assertEquals(-1, t.chargePowerKw, 0);
        assertEquals("AC Şarj", t.chargeMode); assertEquals("Bilgi yok", ChargingInfo.powerText(t.chargePowerKw));
    }
    @Test public void missingDcPowerDoesNotInventOneHundredTwenty() {
        VehicleTelemetry t = parse("\"chargeMode\":\"DC_FAST_CHARGING\"");
        assertTrue(t.isCharging); assertEquals(-1, t.chargePowerKw, 0); assertEquals("DC Şarj", t.chargeMode);
    }
    @Test public void realAcAndDcPowerRemainIndependentOfType() {
        assertEquals(7.4, parse("\"chargeMode\":\"AC\",\"chargePowerKw\":7.4").chargePowerKw, .001);
        VehicleTelemetry dc = parse("\"chargeMode\":\"DC\",\"chargingPower\":83.6");
        assertEquals(83.6, dc.chargePowerKw, .001); assertFalse(dc.chargeMode.contains("11"));
    }
    @Test public void nestedPowerWrapperIsRead() {
        assertEquals(3.6, parse("\"isCharging\":true,\"obcState\":{\"content\":{\"chargingPower\":{\"value\":\"3.6\"}}}").chargePowerKw, .001);
    }
    @Test public void zeroMeasurementIsNotUnknown() {
        VehicleTelemetry t = parse("\"isCharging\":true,\"chargePowerKw\":0");
        assertTrue(t.isCharging); assertEquals(0, t.chargePowerKw, 0); assertTrue(ChargingInfo.knownPower(t.chargePowerKw));
    }
    @Test public void unrelatedPowerAndHistoricalValuesAreIgnored() {
        assertEquals(-1, parse("\"isCharging\":true,\"motor\":{\"power\":150},\"chargingStation\":{\"chargingPower\":180},\"myTrip\":{\"chargingPower\":22}").chargePowerKw, 0);
    }
    @Test public void invalidPowerIsUnknown() {
        for (String value : new String[]{"-1", "null", "\"NaN\"", "\"Infinity\"", "\"SNA\""})
            assertEquals(-1, parse("\"isCharging\":true,\"chargePowerKw\":" + value).chargePowerKw, 0);
    }
    @Test public void inactiveAndExplicitFalseNeverBecomeCharging() {
        for (String mode : new String[]{"INACTIVE", "NOT_CHARGING", "NO_CHARGING", "DC_STOPPED", "AC_COMPLETE"})
            assertFalse(parse("\"chargeMode\":\"" + mode + "\"").isCharging);
        VehicleTelemetry t = parse("\"chargeMode\":\"AC\",\"isCharging\":false,\"chargePowerKw\":11");
        assertFalse(t.isCharging); assertEquals(0,t.chargePowerKw,0);
    }
    @Test public void genericChargingDoesNotInventACLabel() {
        VehicleTelemetry t = parse("\"isCharging\":true");
        assertEquals("Şarj Oluyor", t.chargeMode); assertEquals(-1,t.chargePowerKw,0);
    }
}
