package com.togg.companion.data.remote;

import com.togg.companion.core.GezRouteBridge.GezPoint;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TvaApiClientTest {
    @Test public void singleDestinationUsesOriginalNestedPointSchema() throws Exception {
        JSONObject body = TvaApiClient.buildPlannedJourneyPayload(new GezPoint(41.1, 29.2, "  Hedef  "), null);
        assertEquals(1, body.length());
        JSONObject arrival = body.getJSONObject("arrivalPoint");
        assertEquals(41.1, arrival.getJSONObject("geoLocation").getDouble("latitude"), 0.000001);
        assertEquals(29.2, arrival.getJSONObject("geoLocation").getDouble("longitude"), 0.000001);
        assertEquals("Hedef", arrival.getJSONObject("address").getString("label"));
        assertFalse(body.has("vin"));
        assertFalse(body.has("destination"));
        assertFalse(body.has("stops"));
        assertFalse(body.has("startingPoint"));
    }

    @Test public void realOriginIsIncludedAndUnknownOriginIsOmitted() throws Exception {
        GezPoint target = new GezPoint(41.1, 29.2, null);
        JSONObject body = TvaApiClient.buildPlannedJourneyPayload(target, new GezPoint(-33.9, 18.4, "Başlangıç"));
        assertEquals(-33.9, body.getJSONObject("startingPoint").getJSONObject("geoLocation").getDouble("latitude"), 0.000001);
        assertFalse(TvaApiClient.buildPlannedJourneyPayload(target, new GezPoint(0, 0, null)).has("startingPoint"));
    }

    @Test public void intermediateWaypointsExcludeOriginAndDestinationAndKeepOrder() throws Exception {
        GezPoint origin = new GezPoint(40.1, 29.1, "Başlangıç");
        GezPoint first = new GezPoint(40.2, 29.2, "Birinci durak");
        GezPoint second = new GezPoint(40.3, 29.3, "İkinci durak");
        GezPoint target = new GezPoint(40.4, 29.4, "Varış", Arrays.asList(origin, first, second, new GezPoint(40.4, 29.4, "Varış")));
        JSONObject body = TvaApiClient.buildPlannedJourneyPayload(target, new GezPoint(39, 28, "Eski konum"));
        assertEquals(40.1, body.getJSONObject("startingPoint").getJSONObject("geoLocation").getDouble("latitude"), 0.000001);
        assertEquals(40.4, body.getJSONObject("arrivalPoint").getJSONObject("geoLocation").getDouble("latitude"), 0.000001);
        assertEquals(2, body.getJSONArray("waypoints").length());
        assertEquals("Birinci durak", body.getJSONArray("waypoints").getJSONObject(0).getJSONObject("address").getString("label"));
        assertEquals("İkinci durak", body.getJSONArray("waypoints").getJSONObject(1).getJSONObject("address").getString("label"));
    }

    @Test public void twoStopRouteDoesNotDuplicateStopsAsWaypoints() throws Exception {
        GezPoint target = new GezPoint(41, 29, "Varış", Arrays.asList(new GezPoint(40, 28, "Başlangıç"), new GezPoint(41, 29, "Varış")));
        JSONObject body = TvaApiClient.buildPlannedJourneyPayload(target, null);
        assertEquals(2, body.length());
        assertFalse(body.has("waypoints"));
    }

    @Test public void invalidDestinationsNeverBecomeRequestBodies() throws Exception {
        for (GezPoint bad : Arrays.asList(new GezPoint(0, 0, null), new GezPoint(91, 29, null),
                new GezPoint(41, 181, null), new GezPoint(Double.NaN, 29, null), new GezPoint(41, Double.POSITIVE_INFINITY, null))) {
            try {
                TvaApiClient.buildPlannedJourneyPayload(bad, null);
                fail("Invalid destination must be rejected");
            } catch (IllegalArgumentException expected) { }
        }
    }
}
