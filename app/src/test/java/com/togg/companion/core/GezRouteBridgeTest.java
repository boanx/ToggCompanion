package com.togg.companion.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GezRouteBridgeTest {
    @Test
    public void parsesPlainCoordinates() {
        GezRouteBridge.GezPoint point = GezRouteBridge.parse("40.9912, 29.0234");
        assertNotNull(point);
        assertEquals(40.9912, point.lat, 0.000001);
        assertEquals(29.0234, point.lon, 0.000001);
    }

    @Test
    public void parsesGoogleMapsQueryCoordinates() {
        GezRouteBridge.GezPoint point = GezRouteBridge.parse("https://maps.google.com/?q=41.0082,28.9784");
        assertNotNull(point);
        assertEquals(41.0082, point.lat, 0.000001);
        assertEquals(28.9784, point.lon, 0.000001);
    }

    @Test
    public void buildsShareUrl() {
        String url = GezRouteBridge.buildShareUrl(40.9912, 29.0234, "Kadıköy");
        assertEquals("https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=Kad%C4%B1k%C3%B6y&lat=40.9912&long=29.0234", url);
    }
}
