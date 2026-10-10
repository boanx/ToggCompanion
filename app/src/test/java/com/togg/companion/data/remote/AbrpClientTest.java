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

    @Test public void parsesLatestPlanNextStopResponse() {
        AbrpClient.AbrpPlanStop stop = AbrpClient.parsePlanJson("{\"result\":\"ok\",\"plan\":{\"next_stop\":{\"name\":\"Ankara\",\"lat\":39.9334,\"lon\":32.8597,\"arrival_soc\":42,\"distance_meters\":350000,\"duration_seconds\":12600}}}");
        assertNotNull(stop);
        assertEquals("Ankara", stop.name);
        assertEquals(39.9334, stop.lat, 0.000001);
        assertEquals(32.8597, stop.lon, 0.000001);
        assertEquals(42, stop.arrivalSoc, 0.001);
        assertEquals(350000, stop.distanceMeters);
        assertEquals(12600, stop.durationSeconds);
    }

    @Test public void parsesLatestPlanUpcomingWaypointArray() {
        AbrpClient.AbrpPlanStop stop = AbrpClient.parsePlanJson("{\"data\":{\"plan\":{\"waypoints\":[{\"title\":\"İzmit\",\"location\":{\"latitude\":40.7669,\"longitude\":29.9169},\"soc\":61,\"distance\":90000,\"duration\":3600}]}}}");
        assertNotNull(stop);
        assertEquals("İzmit", stop.name);
        assertEquals(40.7669, stop.lat, 0.000001);
        assertEquals(29.9169, stop.lon, 0.000001);
        assertEquals(61, stop.arrivalSoc, 0.001);
        assertEquals(90000, stop.distanceMeters);
        assertEquals(3600, stop.durationSeconds);
    }

    @Test public void parsePlanJsonRejectsMissingCoordinates() {
        assertNull(AbrpClient.parsePlanJson("{\"plan\":{\"next_stop\":{\"name\":\"No coordinates\"}}}"));
    }
}
