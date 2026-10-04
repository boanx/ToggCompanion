package com.togg.companion.core;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GezRouteBridgeTest {
    @Test public void parsesDirectCoordinates() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseDirect("geo:41.08136, 28.94291");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void parsesLastWaypointFromDirectionsPath() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest("https://www.google.com/maps/dir/40.063805,32.583485/40.4669456,32.363884/40.7957001,30.0792027/41.08136,28.94291/data=abc");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void parsesAllStopsFromDirectionsPath() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest("https://www.google.com/maps/dir/40.063805,32.583485/40.4669456,32.363884/40.7957001,30.0792027/41.08136,28.94291/data=abc");
        assertNotNull(p); assertTrue(p.hasMultipleStops()); assertEquals(4, p.stops.size());
        assertEquals(40.063805, p.stops.get(0).lat, 0.000001);
        assertEquals(40.4669456, p.stops.get(1).lat, 0.000001);
        assertEquals(40.7957001, p.stops.get(2).lat, 0.000001);
        assertEquals(41.08136, p.stops.get(3).lat, 0.000001);
        assertEquals(p.stops.get(3).lat, p.lat, 0.000001);
    }
    @Test public void parsesLastDataCoordinates() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest("https://www.google.com/maps/place/x/data=!3d40.1!4d29.2!3d41.08136!4d28.94291");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void buildsShareUrl() {
        String url = GezRouteBridge.buildShareUrl(40.9912, 29.0234, "Kadıköy");
        assertEquals("https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=Kad%C4%B1k%C3%B6y&lat=40.9912&long=29.0234", url);
    }
}
