package com.togg.companion.data.remote;
import com.togg.companion.core.TelemetryParser;
import com.togg.companion.core.VehicleTelemetry;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class AbrpClientTest {
    private VehicleTelemetry sample(String trips) {
        return TelemetryParser.parse("{\"data\":{\"telemetry\":{\"batterySoc\":80,\"range\":350,\"odometer\":12345,\"isCharging\":true},\"lastTripInfo\":" + trips + "}}", "vin", "plate");
    }
    @Test public void unknownMileageIsNotUploadedAsZero() {
        VehicleTelemetry telemetry = TelemetryParser.parse("{\"data\":{\"telemetry\":{}}}", "vin", "plate");
        assertFalse(AbrpClient.buildTlmJson(telemetry, 88.5).has("odometer"));
    }
    @Test public void sendsBatteryAndOnlyMyTripConsumption() throws Exception {
        JSONObject tlm = AbrpClient.buildTlmJson(sample("{\"myTrip\":{\"distance\":50,\"avgConsumptions\":17.2},\"currentTrip\":{\"distance\":10,\"avgConsumptions\":12}}"), 88.5);
        assertEquals(80, tlm.getInt("soc"));
        assertEquals(17.2, tlm.getDouble("consumption"), 0.001);
        assertEquals(70.8, tlm.getDouble("soe"), 0.001);
    }
    @Test public void missingMyTripDoesNotUseCurrentTrip() {
        assertFalse(AbrpClient.buildTlmJson(sample("{\"currentTrip\":{\"distance\":10,\"avgConsumptions\":12}}"), 88.5).has("consumption"));
    }
    @Test public void emptyMyTripDoesNotInventConsumption() {
        assertFalse(AbrpClient.buildTlmJson(sample("{\"myTrip\":{\"distance\":0,\"avgConsumptions\":17.2}}"), 88.5).has("consumption"));
    }
}
