package com.togg.companion.data.remote;

import com.togg.companion.core.TelemetryParser;
import com.togg.companion.core.VehicleTelemetry;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AbrpClientTest {
    @Test public void buildTlmJsonIncludesRequiredTelemetry() throws Exception {
        String json = "{\"data\":{\"telemetry\":{\"batterySoc\":80,\"range\":350,\"odometer\":12345,\"isCharging\":true,\"location\":{\"latitude\":41.0,\"longitude\":29.0}},\"lastTripInfo\":{\"currentTrip\":{\"avgConsumptions\":12.0}}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "vin", "plate");
        JSONObject tlm = AbrpClient.buildTlmJson(telemetry, 88.5);
        assertTrue(tlm.getLong("utc") > 0);
        assertEquals(80, tlm.getInt("soc"));
        assertEquals(350, tlm.getInt("est_battery_range"));
        assertEquals(12345, tlm.getLong("odometer"));
        assertEquals(88.5, tlm.getDouble("capacity"), 0.001);
        assertEquals(70.8, tlm.getDouble("soe"), 0.001);
        assertEquals(1, tlm.getInt("is_charging"));
        assertEquals(41.0, tlm.getDouble("lat"), 0.001);
        assertEquals(29.0, tlm.getDouble("lon"), 0.001);
        assertEquals(12.0, tlm.getDouble("consumption"), 0.001);
    }

    @Test public void consumptionUsesLastTripThenCurrentTripFallback() throws Exception {
        String json = "{\"data\":{\"telemetry\":{\"batterySoc\":80,\"range\":350,\"odometer\":12345,\"isCharging\":true},\"lastTripInfo\":{\"currentTrip\":{\"avgConsumptions\":12.0}}}}";
        VehicleTelemetry telemetry = TelemetryParser.parse(json, "vin", "plate");
        assertEquals(12.0, AbrpClient.buildTlmJson(telemetry, 88.5).getDouble("consumption"), 0.001);

        String jsonWithFallback = "{\"data\":{\"telemetry\":{\"batterySoc\":80,\"range\":350,\"odometer\":12345,\"isCharging\":true},\"lastTripInfo\":{\"currentTrip\":{\"consumption\":20.0}}}}";
        VehicleTelemetry fallback = TelemetryParser.parse(jsonWithFallback, "vin", "plate");
        assertEquals(20.0, AbrpClient.buildTlmJson(fallback, 88.5).getDouble("consumption"), 0.001);
    }
}
