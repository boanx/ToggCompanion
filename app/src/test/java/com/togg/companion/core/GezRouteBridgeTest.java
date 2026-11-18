package com.togg.companion.core;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GezRouteBridgeTest {
    @Test public void shortLinkRedirectResolvesWithoutRequestingTheFinalCoordinateUrl() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3000);
            java.util.concurrent.atomic.AtomicReference<Exception> failure = new java.util.concurrent.atomic.AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
                    String line; while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    socket.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: https://www.google.com/maps/search/?api=1&query=41.1%2C29.2\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                } catch (Exception error) { failure.set(error); }
            });
            responder.setDaemon(true); responder.start();
            GezRouteBridge.GezPoint point = GezRouteBridge.resolve(null, "http://127.0.0.1:" + server.getLocalPort() + "/short");
            responder.join(3500);
            org.junit.Assert.assertNull(failure.get()); assertNotNull(point);
            assertEquals(41.1, point.lat, 0.000001); assertEquals(29.2, point.lon, 0.000001);
        }
    }
    @Test public void encodedGoogleSearchCoordinateNeedsNoNetwork() throws Exception {
        GezRouteBridge.GezPoint point = GezRouteBridge.resolve(null,
                "https://www.google.com/maps/search/?api=1&query=41.1%2C29.2");
        assertNotNull(point); assertEquals(41.1, point.lat, 0.000001); assertEquals(29.2, point.lon, 0.000001);
    }
    @Test public void googleWrapperUnwrapsOnlyAnActualMapUrl() {
        GezRouteBridge.GezPoint point = GezRouteBridge.parseUrlForTest("https://www.google.com/url?q=https%3A%2F%2Fmaps.google.com%2F%3Fq%3D41.1%252C29.2&sa=D");
        assertNotNull(point); assertEquals(41.1, point.lat, 0.000001);
        assertEquals("https://www.google.com/url?q=https%3A%2F%2Fexample.com", GezRouteBridge.normalizeMapUrl("https://www.google.com/url?q=https%3A%2F%2Fexample.com"));
    }
    @Test public void canonicalMetadataIsReadButUnrelatedScriptCoordinatesAreNot() {
        String url = GezRouteBridge.mapMetadataUrl("<meta content='https://www.google.com/maps/?q=41.1%2C29.2&amp;hl=tr' property='og:url'>");
        assertNotNull(url); assertEquals(41.1, GezRouteBridge.parseUrlForTest(url).lat, 0.000001);
        org.junit.Assert.assertNull(GezRouteBridge.mapMetadataUrl("<script>var viewport=[41.1,29.2];</script><meta property='og:url' content='https://example.com/'>"));
    }
    @Test public void namedPlaceAndSearchViewportAreNotDestinationPins() {
        org.junit.Assert.assertNull(GezRouteBridge.parseUrlForTest("https://www.google.com/maps/place/Example+Street/@40.1,29.2,10z"));
        org.junit.Assert.assertNull(GezRouteBridge.parseUrlForTest("https://www.google.com/maps/search/Example+Street/@40.1,29.2,10z"));
        org.junit.Assert.assertNull(GezRouteBridge.parseUrlForTest("https://maps.google.com/maps?q=Example+Street&ll=40.1,29.2"));
    }
    @Test public void encodedDataCoordinatesAndPathSpacesResolveTogether() {
        GezRouteBridge.GezPoint point = GezRouteBridge.parseUrlForTest("https://www.google.com/maps/dir/40.1,29.2/Example+Street/data=%214m12%214m11%211m1%214e1%211m5%211m4%211splace-id%218m2%213d41.1%214d28.9%212m1%2111b1%213e0");
        assertNotNull(point); assertEquals("Example Street", point.name); assertEquals(41.1, point.lat, 0.000001);
    }
    @Test public void namedDestinationUsesItsEmbeddedLegCoordinates() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest(
                "https://www.google.com/maps/dir/40.1,29.2/Example+Sk.+No:38/"
                + "data=!4m12!4m11!1m1!4e1!1m5!1m4!1splace-id!8m2!3d41.1!4d28.9!2m1!11b1!3e0");
        assertNotNull(p); assertEquals(2, p.stops.size());
        assertEquals(40.1, p.stops.get(0).lat, 0.000001);
        assertEquals(41.1, p.lat, 0.000001); assertEquals(28.9, p.lon, 0.000001);
        assertEquals("Example Sk. No:38", p.name);
    }
    @Test public void originCoordinatesCannotBeAssignedToNamedDestination() {
        GezRouteBridge.GezPoint p = GezRouteBridge.parseUrlForTest(
                "https://www.google.com/maps/dir/Origin/Unknown/"
                + "data=!4m10!4m9!1m5!1m4!1sorigin-id!8m2!3d40.1!4d29.2!1m1!1sunknown!3e0");
        assertNotNull(p); assertEquals(2, p.stops.size());
        assertEquals(40.1, p.stops.get(0).lat, 0.000001);
        assertEquals(0, p.lat, 0.000001); assertEquals(0, p.lon, 0.000001);
        assertEquals("Unknown", p.name);
    }
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
