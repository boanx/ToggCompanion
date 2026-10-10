package com.togg.companion.core;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GezRouteBridgeTest {
    @Test public void parsesDirectCoordinates() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseDirect("geo:41.08136, 28.94291");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void parsesGeoQueryCoordinateWhenBaseIsZero() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseDirect("geo:0,0?q=41.08136,28.94291(Hedef)");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void parsesGeoBaseCoordinateWithLabelQuery() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseDirect("geo:41.08136,28.94291?q=Hedef");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void parsesGoogleNavigationCoordinate() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseDirect("google.navigation:q=41.08136,28.94291");
        assertNotNull(p); assertEquals(41.08136, p.lat, 0.000001); assertEquals(28.94291, p.lon, 0.000001);
    }
    @Test public void parsesDaddrQueryCoordinate() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest("https://maps.google.com/?daddr=41.08136%2C28.94291");
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
    @Test public void parsesQueryParameterDirectionsStops() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest("https://www.google.com/maps/dir/?origin=40.1%2C29.2&waypoints=40.2%2C29.3%7C40.3%2C29.4&destination=40.4%2C29.5");
        assertNotNull(p); assertEquals(4, p.stops.size());
        assertEquals(40.1, p.stops.get(0).lat, 0.000001);
        assertEquals(40.2, p.stops.get(1).lat, 0.000001);
        assertEquals(40.3, p.stops.get(2).lat, 0.000001);
        assertEquals(40.4, p.stops.get(3).lat, 0.000001);
    }
    @Test public void parsesEveryDataCoordinateInOrder() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest("https://maps.google.com/data=!3d40.1!4d29.2!3d40.3!4d29.4!1d29.5!2d40.5");
        assertNotNull(p); assertEquals(3, p.stops.size());
        assertEquals(40.1, p.stops.get(0).lat, 0.000001);
        assertEquals(40.3, p.stops.get(1).lat, 0.000001);
        assertEquals(40.5, p.stops.get(2).lat, 0.000001);
    }
    @Test public void buildsDirectNavigationUri() {
        assertEquals("google.navigation:q=40.9912,29.0234", GezRouteBridge.buildNavigationUri(40.9912, 29.0234));
        assertEquals("geo:40.9912,29.0234?q=40.9912,29.0234(Kad%C4%B1k%C3%B6y)", GezRouteBridge.buildNavigationGeoUri(40.9912, 29.0234, "Kadıköy"));
    }
    @Test public void buildsTwoStopDirectionsUrl() {
        assertEquals("https://www.google.com/maps/dir/?api=1&origin=40.1,29.2&destination=41.1,28.9",
                GezRouteBridge.buildMultiStopDirectionsUrl(Arrays.asList(new GezRouteBridge.GezPoint(40.1, 29.2, null),
                        new GezRouteBridge.GezPoint(41.1, 28.9, null))));
    }
    @Test public void buildsThreeStopDirectionsUrl() {
        assertEquals("https://www.google.com/maps/dir/?api=1&origin=40.1,29.2&destination=42.1,27.9&waypoints=41.1,28.9",
                GezRouteBridge.buildMultiStopDirectionsUrl(Arrays.asList(new GezRouteBridge.GezPoint(40.1, 29.2, null),
                        new GezRouteBridge.GezPoint(41.1, 28.9, null), new GezRouteBridge.GezPoint(42.1, 27.9, null))));
    }
    @Test public void buildsFourStopDirectionsUrlWithOrderedWaypoints() {
        assertEquals("https://www.google.com/maps/dir/?api=1&origin=40.1,29.2&destination=43.1,26.9&waypoints=41.1,28.9%7C42.1,27.9",
                GezRouteBridge.buildMultiStopDirectionsUrl(Arrays.asList(new GezRouteBridge.GezPoint(40.1, 29.2, null),
                        new GezRouteBridge.GezPoint(41.1, 28.9, null), new GezRouteBridge.GezPoint(42.1, 27.9, null),
                        new GezRouteBridge.GezPoint(43.1, 26.9, null))));
    }
    @Test public void buildsShareUrl() {
        String url = GezRouteBridge.buildShareUrl(40.9912, 29.0234, "Kadıköy");
        assertEquals("https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=Kad%C4%B1k%C3%B6y&lat=40.9912&long=29.0234", url);
    }
    @Test public void trimsShareUrlNameAndUsesFallbackForBlankName() {
        assertEquals("https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=Kad%C4%B1k%C3%B6y&lat=40.9912&long=29.0234",
                GezRouteBridge.buildShareUrl(40.9912, 29.0234, "  Kadıköy  "));
        assertEquals("https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=Hedef&lat=40.9912&long=29.0234",
                GezRouteBridge.buildShareUrl(40.9912, 29.0234, "  "));
    }
    @Test public void buildsNativeDeepLink() {
        assertEquals("trumore-superapp://custom/redirection?placeType=PlaceTypeDO.unknown&placeName=Kad%C4%B1k%C3%B6y&lat=40.9912&long=29.0234",
                GezRouteBridge.buildNativeDeepLink(40.9912, 29.0234, "Kadıköy"));
    }
    @Test public void buildsBetaDeepLink() {
        assertEquals("trumore-superapp-beta://custom/redirection?placeType=PlaceTypeDO.unknown&placeName=Kad%C4%B1k%C3%B6y&lat=40.9912&long=29.0234",
                GezRouteBridge.buildBetaDeepLink(40.9912, 29.0234, "Kadıköy"));
    }
    @Test public void buildsUniversalGeoUri() {
        assertEquals("geo:40.9912,29.0234?q=40.9912,29.0234(Kad%C4%B1k%C3%B6y)",
                GezRouteBridge.buildUniversalGeoUri(40.9912, 29.0234, "Kadıköy"));
        assertEquals("geo:40.9912,29.0234?q=40.9912,29.0234",
                GezRouteBridge.buildUniversalGeoUri(40.9912, 29.0234, "  "));
    }
    @Test public void buildsTrumoreCarDisplayDirectionsLink() {
        assertEquals("trumore-superapp://custom/redirection?path=/navigation/carDisplayDirections&lat=41.1&long=29.2&placeName=Kad%C4%B1k%C3%B6y",
                GezRouteBridge.buildCarDisplayDirectionsLink(41.1, 29.2, "Kadıköy"));
    }
}
