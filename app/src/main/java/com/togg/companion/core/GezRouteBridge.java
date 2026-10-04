package com.togg.companion.core;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GezRouteBridge {
    private static final Pattern COORDINATES = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECTIONS = Pattern.compile("(?:/maps/dir/|/dir/)([^?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACE = Pattern.compile("/place/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PARAM = Pattern.compile("[?&](?:q|query|placeName|name)=([^&#]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COORD_PARAM = Pattern.compile("[?&](?:q|query|coordinate|ll|destination)=(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_COORD = Pattern.compile("/@(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern DATA_COORD = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern PREVIEW_COORD = Pattern.compile("\\[null,\\s*null,\\s*(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]");
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private GezRouteBridge() {}

    public interface Callback {
        void onResolved(GezPoint point);
        void onError(String error);
    }

    public static final class GezPoint {
        public final double lat;
        public final double lon;
        public final String name;
        private GezPoint(double lat, double lon, String name) { this.lat = lat; this.lon = lon; this.name = name; }
    }

    public static GezPoint parseDirect(String text) {
        if (text == null) return null;
        String source = text.trim();
        if (source.regionMatches(true, 0, "http://", 0, 7) || source.regionMatches(true, 0, "https://", 0, 8)) return null;
        if (source.regionMatches(true, 0, "geo:", 0, 4)) source = source.substring(4);
        Matcher matcher = COORDINATES.matcher(source);
        return matcher.find() ? point(matcher.group(1), matcher.group(2), "Hedef Konum") : null;
    }

    /** Kept for existing synchronous callers; URL expansion and geocoding use resolve(). */
    public static GezPoint parse(String text) { return parseDirect(text); }

    static GezPoint parseUrlForTest(String url) {
        Matcher dirs = DIRECTIONS.matcher(url);
        if (dirs.find()) {
            String[] segments = dirs.group(1).split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                String segment = segments[i];
                if (segment.isEmpty() || segment.startsWith("data=") || segment.startsWith("am=")) continue;
                Matcher c = COORDINATES.matcher(segment);
                if (c.matches()) return point(c.group(1), c.group(2), null);
                break;
            }
        }
        String[] match = lastMatch(DATA_COORD, url);
        return match == null ? null : point(match[0], match[1], null);
    }

    public static GezPoint resolve(Context context, String input) throws IOException {
        GezPoint direct = parseDirect(input);
        if (direct != null) return direct;
        if (input == null || input.trim().isEmpty()) throw new IOException("Bir adres veya harita bağlantısı girin.");
        String source = input.trim();
        Matcher urlMatcher = URL_PATTERN.matcher(source);
        if (urlMatcher.find()) {
            String url = urlMatcher.group().replaceAll("[.,;]+$", "");
            url = followRedirects(url);
            String name = null;
            Matcher dirs = DIRECTIONS.matcher(url);
            if (dirs.find()) {
                String[] segments = dirs.group(1).split("/");
                for (int i = segments.length - 1; i >= 0; i--) {
                    String segment = segments[i];
                    if (segment.isEmpty() || segment.startsWith("data=") || segment.startsWith("am=")) continue;
                    Matcher coords = COORDINATES.matcher(segment);
                    if (coords.matches()) return withResolvedName(context, point(coords.group(1), coords.group(2), null));
                    name = decode(segment);
                    break;
                }
            }
            String[] match = lastMatch(DATA_COORD, url);
            if (match == null) match = lastMatch(PREVIEW_COORD, url);
            if (match == null) match = firstMatch(COORD_PARAM, url);
            if (match == null) match = firstMatch(AT_COORD, url);
            if (match != null) return withResolvedName(context, point(match[0], match[1], name));
            Matcher place = PLACE.matcher(url);
            if (name == null && place.find()) name = decode(place.group(1));
            Matcher nameParam = NAME_PARAM.matcher(url);
            if (name == null && nameParam.find()) name = decode(nameParam.group(1));
            if (name != null) return forwardGeocode(context, name);
            throw new IOException("Bağlantıda hedef koordinat bulunamadı.");
        }
        return forwardGeocode(context, source);
    }

    public static void resolveAsync(Context context, String input, Callback callback) {
        Context appContext = context == null ? null : context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                GezPoint result = resolve(appContext, input);
                new Handler(Looper.getMainLooper()).post(() -> callback.onResolved(result));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Adres çözümlenemedi." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(message));
            }
        });
    }

    private static String followRedirects(String url) throws IOException {
        for (int i = 0; i < 10; i++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 ToggCompanion");
            int status;
            try { status = connection.getResponseCode(); }
            finally { }
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                if (location == null) throw new IOException("Harita bağlantısı yönlendirmesi geçersiz.");
                url = new URL(new URL(url), location).toString();
            } else return url;
        }
        throw new IOException("Harita bağlantısı çok fazla yönlendirme içeriyor.");
    }

    private static GezPoint forwardGeocode(Context context, String name) throws IOException {
        if (context == null || !Geocoder.isPresent()) throw new IOException("Adres koordinatlara çevrilemedi.");
        List<Address> results = new Geocoder(context).getFromLocationName(name, 1);
        if (results == null || results.isEmpty()) throw new IOException("Adres koordinatlara çevrilemedi.");
        Address address = results.get(0);
        return new GezPoint(address.getLatitude(), address.getLongitude(), friendlyName(address, name));
    }

    private static GezPoint withResolvedName(Context context, GezPoint p) throws IOException {
        if (p == null) throw new IOException("Bağlantıdaki koordinatlar geçersiz.");
        if (context == null || p.name != null && !p.name.trim().isEmpty() && !p.name.equals(p.lat + ", " + p.lon) || !Geocoder.isPresent()) return p;
        List<Address> results = new Geocoder(context).getFromLocation(p.lat, p.lon, 1);
        if (results == null || results.isEmpty()) return p;
        Address address = results.get(0);
        return new GezPoint(p.lat, p.lon, friendlyName(address, p.lat + ", " + p.lon));
    }

    private static String friendlyName(Address a, String fallback) {
        String locality = a.getSubLocality();
        if (locality == null || locality.isEmpty()) locality = a.getLocality();
        String admin = a.getAdminArea();
        if (locality != null && admin != null && !locality.equals(admin)) return locality + ", " + admin;
        String line = a.getAddressLine(0);
        return line == null || line.isEmpty() ? fallback : line;
    }

    private static GezPoint point(String latText, String lonText, String name) {
        try {
            double lat = Double.parseDouble(latText), lon = Double.parseDouble(lonText);
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new GezPoint(lat, lon, name);
        } catch (NumberFormatException e) { return null; }
    }
    private static String[] firstMatch(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? new String[]{m.group(1), m.group(2)} : null;
    }
    private static String[] lastMatch(Pattern p, String s) {
        Matcher m = p.matcher(s);
        String[] last = null;
        while (m.find()) last = new String[]{m.group(1), m.group(2)};
        return last;
    }
    private static String decode(String s) { try { return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); } catch (Exception e) { return s; } }

    public static String buildShareUrl(double lat, double lon, String name) {
        String encoded = URLEncoder.encode(name != null ? name : "Hedef", StandardCharsets.UTF_8);
        return "https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=" + encoded + "&lat=" + lat + "&long=" + lon;
    }

    public static boolean openInTrumore(Context context, double lat, double lon, String name) {
        Uri uri = Uri.parse(buildShareUrl(lat, lon, name));
        for (String pkg : new String[]{"com.togg.toggmobile", "com.togg.trumore", null}) {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (pkg != null) intent.setPackage(pkg);
            try { context.startActivity(intent); return true; } catch (Exception ignored) { }
        }
        return false;
    }
}
