package com.togg.companion.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GezRouteBridge {
    private static final Pattern COORDINATES = Pattern.compile("([-+]?[0-9]{1,3}(?:\\.[0-9]+)?)[,\\s]+([-+]?[0-9]{1,3}(?:\\.[0-9]+)?)");
    private static final Pattern PARAM_COORDINATES = Pattern.compile("(?:[?&](?:q|destination)=|@|[?&]lat=)([-+]?[0-9]{1,3}(?:\\.[0-9]+)?)[,\\s]+(?:[?&]lon(?:g)?=)?([-+]?[0-9]{1,3}(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAT_LON = Pattern.compile("[?&]lat=([-+]?[0-9]{1,3}(?:\\.[0-9]+)?)[^#]*?[&]lon(?:g)?=([-+]?[0-9]{1,3}(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACE_NAME = Pattern.compile("[?&](?:placeName|name)=([^&#]*)", Pattern.CASE_INSENSITIVE);

    private GezRouteBridge() {}

    public static final class GezPoint {
        public final double lat;
        public final double lon;
        public final String name;

        private GezPoint(double lat, double lon, String name) {
            this.lat = lat;
            this.lon = lon;
            this.name = name;
        }
    }

    public static GezPoint parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String source = text.trim();
        if (source.regionMatches(true, 0, "geo:", 0, 4)) source = source.substring(4);
        Matcher nameMatcher = PLACE_NAME.matcher(source);
        String name = nameMatcher.find() ? Uri.decode(nameMatcher.group(1).replace('+', ' ')) : "Hedef Konum";
        Matcher matcher = LAT_LON.matcher(source);
        boolean found = matcher.find();
        if (!found) {
            matcher = PARAM_COORDINATES.matcher(source);
            found = matcher.find();
        }
        if (!found) {
            matcher = COORDINATES.matcher(source);
            found = matcher.find();
        }
        if (!found) return null;
        try {
            double lat = Double.parseDouble(matcher.group(1));
            double lon = Double.parseDouble(matcher.group(2));
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new GezPoint(lat, lon, name.isEmpty() ? "Hedef Konum" : name);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String buildShareUrl(double lat, double lon, String name) {
        String encoded = URLEncoder.encode(name != null ? name : "Hedef", StandardCharsets.UTF_8);
        return "https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName="
                + encoded + "&lat=" + lat + "&long=" + lon;
    }

    public static boolean openInTrumore(Context context, double lat, double lon, String name) {
        Uri uri = Uri.parse(buildShareUrl(lat, lon, name));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            intent.setPackage("com.togg.trumore");
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }
}
