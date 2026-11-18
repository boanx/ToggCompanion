package com.togg.companion.core;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.data.remote.TvaApiClient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GezRouteBridge {
    private static final Pattern COORDINATES = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECTIONS = Pattern.compile("(?:/maps/dir/|/dir/)([^?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACE = Pattern.compile("/(?:place|search)/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PARAM = Pattern.compile("[?&](?:q|query|placeName|name)=([^&#]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_COORD = Pattern.compile("/@(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern DATA_COORD = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern DATA_COORD_REVERSED = Pattern.compile("!1d(-?\\d+(?:\\.\\d+)?)!2d(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern PREVIEW_COORD = Pattern.compile("\\[null,\\s*null,\\s*(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]");
    private static final Pattern GEO_QUERY_COORD = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)(?:\\([^)]*\\))?$");
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private GezRouteBridge() {}

    public interface Callback {
        void onResolved(GezPoint point);
        void onError(String error);
    }
    public interface CloudCallback {
        void onSuccess(String message);
        void onError(String message);
        default void onAccepted(String message) { onSuccess(message); }
        default void onUncertain(String message) { onError(message); }
    }

    public static final class GezPoint {
        public final double lat;
        public final double lon;
        public final String name;
        public final List<GezPoint> stops;
        public final boolean stopsIncludeOrigin;
        public GezPoint(double lat, double lon, String name) { this(lat, lon, name, null); }
        public GezPoint(double lat, double lon, String name, List<GezPoint> stops) {
            this(lat, lon, name, stops, true);
        }
        private GezPoint(double lat, double lon, String name, List<GezPoint> stops, boolean stopsIncludeOrigin) {
            this.lat = lat; this.lon = lon; this.name = name;
            this.stops = stops != null ? Collections.unmodifiableList(new ArrayList<>(stops)) : Collections.emptyList();
            this.stopsIncludeOrigin = stopsIncludeOrigin;
        }
        public boolean hasMultipleStops() { return stops.size() > 1; }
        /** Ordered future destinations; their first item is not the vehicle's origin. */
        public static GezPoint upcomingDestinations(List<GezPoint> destinations) {
            if (destinations == null || destinations.isEmpty()) throw new IllegalArgumentException("Rota hedefi bulunamadı.");
            GezPoint last = destinations.get(destinations.size() - 1);
            return new GezPoint(last.lat, last.lon, last.name, destinations, false);
        }
    }

    public static GezPoint parseDirect(String text) {
        if (text == null) return null;
        String source = text.trim();
        if (URL_PATTERN.matcher(source).find()) return null;
        if (source.regionMatches(true, 0, "google.navigation:", 0, 18)) {
            String query = source.substring(18);
            int q = query.indexOf("q=");
            if (q >= 0) {
                String value = query.substring(q + 2).split("&", 2)[0];
                Matcher coords = GEO_QUERY_COORD.matcher(decode(value));
                if (coords.matches()) return point(coords.group(1), coords.group(2), "Hedef Konum");
            }
            return null;
        }
        if (source.regionMatches(true, 0, "geo:", 0, 4)) {
            String geo = source.substring(4);
            int queryIndex = geo.indexOf('?');
            String base = queryIndex >= 0 ? geo.substring(0, queryIndex) : geo;
            String query = queryIndex >= 0 ? geo.substring(queryIndex + 1) : "";
            Matcher baseCoords = COORDINATES.matcher(base);
            GezPoint basePoint = baseCoords.matches() ? point(baseCoords.group(1), baseCoords.group(2), "Hedef Konum") : null;
            String qValue = queryValue(query, "q");
            if (qValue != null) {
                Matcher queryCoords = GEO_QUERY_COORD.matcher(qValue);
                if (queryCoords.matches()) {
                    GezPoint queryPoint = point(queryCoords.group(1), queryCoords.group(2), "Hedef Konum");
                    if (queryPoint != null && (basePoint == null || basePoint.lat == 0 && basePoint.lon == 0)) return queryPoint;
                }
                if (basePoint == null || basePoint.lat == 0 && basePoint.lon == 0) return null;
            }
            return basePoint;
        }
        Matcher matcher = COORDINATES.matcher(source);
        return matcher.find() ? point(matcher.group(1), matcher.group(2), "Hedef Konum") : null;
    }

    /** Kept for existing synchronous callers; URL expansion and geocoding use resolve(). */
    public static GezPoint parse(String text) { return parseDirect(text); }

    static GezPoint parseUrlForTest(String url) {
        url = normalizeMapUrl(url);
        url = url.replace("%21", "!").replaceAll("(?i)%2c", ",");
        List<GezPoint> queryStops = parseQueryStops(url);
        if (!queryStops.isEmpty()) return destinationWithStops(queryStops);
        Matcher dirs = DIRECTIONS.matcher(url);
        if (dirs.find()) {
            String[] segments = dirs.group(1).split("/");
            List<GezPoint> stops = parseDirectionStopsWithData(segments, url);
            if (!stops.isEmpty()) return destinationWithStops(stops);
        }
        List<GezPoint> dataStops = parseDataStops(url);
        if (dataStops.size() > 1) return destinationWithStops(dataStops);
        String[] match = lastMatch(DATA_COORD, url);
        if (match == null) match = lastMatch(DATA_COORD_REVERSED, url);
        if (match != null) return point(match[0], match[1], null);
        int queryStart = url.indexOf('?');
        if (queryStart >= 0) {
            boolean namedTarget = false;
            for (String key : new String[]{"q", "query", "coordinate", "ll"}) {
                if ("ll".equals(key) && namedTarget) continue;
                String value = queryValue(url.substring(queryStart + 1).split("#", 2)[0], key);
                if (value == null) continue;
                value = value.replaceFirst("(?i)^(?:loc:|geo:)", "");
                Matcher coords = GEO_QUERY_COORD.matcher(value);
                if (coords.matches()) return point(coords.group(1), coords.group(2), null);
                if (("q".equals(key) || "query".equals(key)) && !value.trim().isEmpty()) namedTarget = true;
            }
        }
        // A named place's @ coordinate is only the viewport and may be far from its pin.
        if (!PLACE.matcher(url).find() && !DIRECTIONS.matcher(url).find() && !NAME_PARAM.matcher(url).find()) {
            match = firstMatch(AT_COORD, url);
            if (match != null) return point(match[0], match[1], null);
        }
        return null;
    }

    public static GezPoint resolve(Context context, String input) throws IOException {
        // A list of coordinate lines is a route, not its first matching coordinate.
        GezPoint direct = input != null && !input.contains("\n") && !input.contains("\r") ? parseDirect(input) : null;
        if (direct != null) return direct;
        if (input == null || input.trim().isEmpty()) throw new IOException("Bir adres veya harita bağlantısı girin.");
        String source = input.trim();
        Matcher urlMatcher = URL_PATTERN.matcher(source);
        if (urlMatcher.find()) {
            String url = urlMatcher.group().replaceAll("[.,;]+$", "");
            url = normalizeMapUrl(url);
            GezPoint known = parseUrlForTest(url);
            if (known != null && hasResolvedStops(known)) return resolvedPoint(context, known);
            url = normalizeMapUrl(followRedirects(url));
            known = parseUrlForTest(url);
            if (known != null && hasResolvedStops(known)) return resolvedPoint(context, known);
            List<GezPoint> queryStops = parseQueryStops(url);
            if (!queryStops.isEmpty()) {
                List<GezPoint> resolvedStops = new ArrayList<>();
                for (GezPoint stop : queryStops) resolvedStops.add(resolveNamedOrCoordinate(context, stop));
                return destinationWithStops(resolvedStops);
            }
            String name = null;
            Matcher dirs = DIRECTIONS.matcher(url);
            if (dirs.find()) {
                String[] segments = dirs.group(1).split("/");
                List<GezPoint> stops = parseDirectionStopsWithData(segments, url);
                if (!stops.isEmpty()) {
                    List<GezPoint> resolvedStops = new ArrayList<>();
                    for (int i = 0; i < stops.size(); i++) {
                        GezPoint stop = stops.get(i);
                        resolvedStops.add(resolveNamedOrCoordinate(context, stop));
                    }
                    return destinationWithStops(resolvedStops);
                }
                for (int i = segments.length - 1; i >= 0; i--) {
                    String segment = segments[i];
                    if (segment.isEmpty() || segment.startsWith("data=") || segment.startsWith("am=")) continue;
                    name = decode(segment); break;
                }
            }
            List<GezPoint> dataStops = parseDataStops(url);
            if (dataStops.size() > 1) {
                List<GezPoint> resolvedStops = new ArrayList<>();
                for (GezPoint stop : dataStops) resolvedStops.add(withResolvedName(context, stop));
                return destinationWithStops(resolvedStops);
            }
            String[] match = lastMatch(DATA_COORD, url);
            if (match == null) match = lastMatch(DATA_COORD_REVERSED, url);
            if (match == null) match = lastMatch(PREVIEW_COORD, url);
            if (match == null && name == null && !PLACE.matcher(url).find() && !NAME_PARAM.matcher(url).find()) match = firstMatch(AT_COORD, url);
            if (match != null) return withResolvedName(context, point(match[0], match[1], name));
            Matcher place = PLACE.matcher(url);
            if (name == null && place.find()) name = decode(place.group(1));
            Matcher nameParam = NAME_PARAM.matcher(url);
            if (name == null && nameParam.find()) name = decode(nameParam.group(1));
            if (name != null) return forwardGeocode(context, name);
            throw new IOException("Bağlantıda hedef koordinat bulunamadı.");
        }
        if (source.contains("\n") || source.contains("\r")) {
            List<GezPoint> stops = new ArrayList<>();
            for (String line : source.split("\\R")) {
                String item = line.trim();
                if (item.isEmpty()) continue;
                GezPoint coordinate = parseDirect(item);
                if (coordinate == null && COORDINATES.matcher(item).matches()) throw new IOException("Rotadaki koordinatlar geçersiz.");
                stops.add(coordinate != null ? coordinate : forwardGeocode(context, item));
            }
            if (!stops.isEmpty()) return destinationWithStops(stops);
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

    private static boolean hasResolvedStops(GezPoint point) {
        if (point.hasMultipleStops()) {
            for (GezPoint stop : point.stops) if (stop == null || stop.lat == 0 && stop.lon == 0 && stop.name != null) return false;
        }
        return !(point.lat == 0 && point.lon == 0 && point.name != null);
    }
    private static GezPoint resolvedPoint(Context context, GezPoint point) throws IOException {
        if (!point.hasMultipleStops()) return withResolvedName(context, point);
        List<GezPoint> stops = new ArrayList<>();
        for (GezPoint stop : point.stops) stops.add(resolveNamedOrCoordinate(context, stop));
        return new GezPoint(point.lat, point.lon, point.name, stops, point.stopsIncludeOrigin);
    }
    private static boolean googleHost(String url) {
        try {
            String host = new URL(url).getHost().toLowerCase(java.util.Locale.ROOT);
            return host.equals("google.com") || host.endsWith(".google.com") || host.equals("google.com.tr")
                    || host.endsWith(".google.com.tr") || host.equals("maps.app.goo.gl") || host.equals("goo.gl");
        } catch (Exception invalid) { return false; }
    }
    static String normalizeMapUrl(String url) {
        String current = url.replace("&amp;", "&");
        for (int i = 0; i < 5 && googleHost(current); i++) {
            int query = current.indexOf('?'); if (query < 0) break;
            String next = null;
            for (String key : new String[]{"url", "link", "deep_link_id", "continue", "q"}) {
                String candidate = queryValue(current.substring(query + 1).split("#", 2)[0], key);
                if (candidate != null && googleHost(candidate) && (candidate.startsWith("https://") || candidate.startsWith("http://"))) { next = candidate; break; }
            }
            if (next == null || next.equals(current)) break;
            current = next;
        }
        return current;
    }
    /** Only canonical/og:url metadata, never arbitrary numbers from scripts or map viewport JSON. */
    static String mapMetadataUrl(String html) {
        Matcher tags = Pattern.compile("<(?:meta|link)\\b[^>]{1,8192}>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            java.util.Map<String,String> attributes = new java.util.HashMap<>();
            Matcher attr = Pattern.compile("([a-z:-]+)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(tags.group());
            while (attr.find()) attributes.put(attr.group(1).toLowerCase(java.util.Locale.ROOT), attr.group(2));
            String value = "og:url".equalsIgnoreCase(attributes.get("property")) ? attributes.get("content")
                    : "canonical".equalsIgnoreCase(attributes.get("rel")) ? attributes.get("href") : null;
            if (value != null && googleHost(value)) return normalizeMapUrl(value);
        }
        return null;
    }
    private static String followRedirects(String url) throws IOException {
        for (int i = 0; i < 10; i++) {
            url = normalizeMapUrl(url);
            GezPoint known = parseUrlForTest(url);
            if (known != null && hasResolvedStops(known)) return url;
            URL target = new URL(url);
            if (!"https".equalsIgnoreCase(target.getProtocol()) && !"http".equalsIgnoreCase(target.getProtocol())) throw new IOException("Harita bağlantısı geçersiz.");
            HttpURLConnection connection = (HttpURLConnection) target.openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                connection.setRequestMethod("GET"); connection.setRequestProperty("User-Agent", "Mozilla/5.0 ToggCompanion");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Harita bağlantısı yönlendirmesi geçersiz.");
                    url = new URL(target, location).toString(); continue;
                }
                if (status < 200 || status >= 300) throw new IOException("Harita bağlantısı açılamadı (HTTP " + status + ").");
                if (googleHost(url) && connection.getContentType() != null && connection.getContentType().toLowerCase(java.util.Locale.ROOT).contains("text/html")) {
                    StringBuilder html = new StringBuilder();
                    try (java.io.Reader reader = new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                        char[] buffer = new char[4096]; int size;
                        while (html.length() < 131072 && (size = reader.read(buffer, 0, Math.min(buffer.length, 131072 - html.length()))) > 0) html.append(buffer, 0, size);
                    }
                    String canonical = mapMetadataUrl(html.toString());
                    if (canonical != null && !canonical.equals(url)) { url = canonical; continue; }
                }
                return url;
            } finally { connection.disconnect(); }
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
        List<Address> results;
        try { results = new Geocoder(context).getFromLocation(p.lat, p.lon, 1); }
        catch (IOException unavailable) { return p; } // A label lookup is optional when coordinates are known.
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
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new GezPoint(lat, lon, name);
        } catch (NumberFormatException e) { return null; }
    }
    private static List<GezPoint> parseDirectionStops(String[] segments) {
        List<GezPoint> stops = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || segment.startsWith("data=") || segment.startsWith("am=")) continue;
            String decoded = decode(segment);
            // @lat,lon,zoom is the map viewport, never a route stop.
            if (decoded.startsWith("@") || decoded.startsWith("!")) continue;
            Matcher coords = COORDINATES.matcher(decoded);
            if (coords.matches()) {
                GezPoint p = point(coords.group(1), coords.group(2), null);
                if (p == null) throw new IllegalArgumentException("Rotadaki koordinatlar geçersiz.");
                stops.add(p);
            }
            else if (!decoded.contains("=")) stops.add(new GezPoint(0, 0, decoded));
        }
        return stops;
    }

    /** Match embedded coordinates to their own route leg, never to a map viewport
     * or to a different named stop. Google's !NmK counts the next K wire tokens. */
    private static List<GezPoint> parseDirectionStopsWithData(String[] segments, String url) {
        List<GezPoint> stops = parseDirectionStops(segments);
        int start = url.indexOf("data=");
        if (start < 0 || stops.isEmpty()) return stops;
        String data = decode(url.substring(start + 5).split("[?&#]", 2)[0]);
        String[] tokens = data.split("!");
        // Only the Maps directions envelope is supported here. Unknown structures
        // retain geocoding rather than guessing a correspondence between stops.
        if (tokens.length < 4 || !tokens[1].matches("4m\\d+") || !tokens[2].matches("4m\\d+")) return stops;
        List<GezPoint> hints = new ArrayList<>();
        try {
            int routeSize = Integer.parseInt(tokens[2].substring(2));
            if (routeSize > tokens.length - 3) return stops;
            int routeEnd = 3 + routeSize;
            for (int i = 3; i < routeEnd && tokens[i].matches("1m\\d+");) {
                int size = Integer.parseInt(tokens[i].substring(2));
                if (size > routeEnd - i - 1) return stops;
                int end = i + 1 + size;
                StringBuilder block = new StringBuilder();
                for (int j = i + 1; j < end; j++) block.append('!').append(tokens[j]);
                List<GezPoint> coordinates = parseDataStops(block.toString());
                if (coordinates.size() > 1) return stops;
                hints.add(coordinates.isEmpty() ? null : coordinates.get(0));
                i = end;
            }
        } catch (NumberFormatException invalid) { return stops; }
        if (hints.size() != stops.size()) return stops;
        for (int i = 0; i < stops.size(); i++) {
            GezPoint stop = stops.get(i), hint = hints.get(i);
            if (hint != null && stop.lat == 0 && stop.lon == 0 && stop.name != null)
                stops.set(i, new GezPoint(hint.lat, hint.lon, stop.name));
        }
        return stops;
    }
    private static GezPoint resolveNamedOrCoordinate(Context context, GezPoint stop) throws IOException {
        if (stop == null) throw new IOException("Rotadaki koordinatlar geçersiz.");
        if (stop.lat == 0 && stop.lon == 0 && stop.name != null) return forwardGeocode(context, stop.name);
        return withResolvedName(context, stop);
    }
    private static List<GezPoint> parseQueryStops(String url) {
        String query = url;
        int q = query.indexOf('?');
        if (q < 0) return Collections.emptyList();
        query = query.substring(q + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) query = query.substring(0, hash);
        String origin = queryValue(query, "origin"), destination = queryValue(query, "destination");
        if (origin == null) origin = queryValue(query, "saddr");
        if (destination == null) destination = queryValue(query, "destination");
        if (destination == null) destination = queryValue(query, "daddr");
        if (origin == null && destination == null) return Collections.emptyList();
        List<GezPoint> stops = new ArrayList<>();
        addQueryStop(stops, origin);
        String waypoints = queryValue(query, "waypoints");
        if (waypoints != null) for (String value : waypoints.split("\\|")) addQueryStop(stops, value);
        addQueryStop(stops, destination);
        return stops;
    }
    private static String queryValue(String query, String key) {
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 0 || !pair.substring(0, equals).equalsIgnoreCase(key)) continue;
            try { return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8.name()); }
            catch (Exception ignored) { return pair.substring(equals + 1); }
        }
        return null;
    }
    private static void addQueryStop(List<GezPoint> stops, String value) {
        if (value == null || value.trim().isEmpty()) return;
        String item = value.trim();
        Matcher coords = COORDINATES.matcher(item);
        stops.add(coords.matches() ? point(coords.group(1), coords.group(2), null) : new GezPoint(0, 0, item));
    }
    private static List<GezPoint> parseDataStops(String url) {
        List<GezPoint> stops = new ArrayList<>();
        Pattern either = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)|!1d(-?\\d+(?:\\.\\d+)?)!2d(-?\\d+(?:\\.\\d+)?)");
        Matcher m = either.matcher(url);
        while (m.find()) {
            GezPoint p = m.group(1) != null ? point(m.group(1), m.group(2), null) : point(m.group(4), m.group(3), null);
            if (p != null) stops.add(p);
        }
        return stops;
    }
    private static GezPoint destinationWithStops(List<GezPoint> stops) {
        GezPoint last = stops.get(stops.size() - 1);
        return new GezPoint(last.lat, last.lon, last.name, stops);
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
    private static String decode(String s) { try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; } }
    private static String encodeUriComponent(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }

    public static String buildShareUrl(double lat, double lon, String name) {
        String safeName = name == null || name.trim().isEmpty() ? "Hedef" : name.trim();
        return "https://superapp-api.channels.togg.cloud/ShareLocation?placeType=PlaceTypeDO.unknown&placeId&placeName=" + encodeUriComponent(safeName) + "&lat=" + lat + "&long=" + lon;
    }

    public static String buildMultiStopDirectionsUrl(List<GezPoint> stops) {
        if (stops == null || stops.isEmpty()) throw new IllegalArgumentException("At least one route stop is required");
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1&origin=");
        GezPoint first = stops.get(0);
        GezPoint last = stops.get(stops.size() - 1);
        url.append(first.lat).append(',').append(first.lon)
                .append("&destination=").append(last.lat).append(',').append(last.lon);
        if (stops.size() > 2) {
            url.append("&waypoints=");
            for (int i = 1; i < stops.size() - 1; i++) {
                if (i > 1) url.append("%7C");
                GezPoint waypoint = stops.get(i);
                url.append(waypoint.lat).append(',').append(waypoint.lon);
            }
        }
        return url.toString();
    }

    public static String buildNativeDeepLink(double lat, double lon, String name) {
        String encoded = encodeUriComponent(name != null ? name : "Hedef");
        return "trumore-superapp://custom/redirection?placeType=PlaceTypeDO.unknown&placeName=" + encoded + "&lat=" + lat + "&long=" + lon;
    }

    public static String buildBetaDeepLink(double lat, double lon, String name) {
        String encoded = encodeUriComponent(name != null ? name : "Hedef");
        return "trumore-superapp-beta://custom/redirection?placeType=PlaceTypeDO.unknown&placeName=" + encoded + "&lat=" + lat + "&long=" + lon;
    }

    public static String buildCarDisplayDirectionsLink(double lat, double lon, String name) {
        return "trumore-superapp://custom/redirection?path=/navigation/carDisplayDirections&lat=" + lat
                + "&long=" + lon + "&placeName=" + encodeUriComponent(name == null || name.trim().isEmpty() ? "Hedef" : name.trim());
    }

    public static boolean openCarDisplayDirections(Context context, double lat, double lon, String name) {
        String link = buildCarDisplayDirectionsLink(lat, lon, name);
        Intent[] intents = {
                new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("com.togg.toggmobile"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("com.togg.trumore"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(link))
        };
        for (Intent intent : intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(intent); return true; } catch (Exception ignored) { }
        }
        return false;
    }

    /** Cloud-only transfer. Failure never launches Trumore or another app. */
    public static void dispatchToVehicleCloud(Context context, SecureTokenStorage storage, double lat, double lon, String name, CloudCallback callback) {
        dispatchToVehicleCloud(context, storage, new GezPoint(lat, lon, name), callback);
    }

    public static void dispatchToVehicleCloud(Context context, SecureTokenStorage storage, GezPoint point, CloudCallback callback) {
        if (callback == null) return;
        if (point == null) { callback.onError("Rota hedefi bulunamadı."); return; }
        if (storage == null) { callback.onError("Togg ID oturumu kullanılamıyor."); return; }
        if (storage.isDemoMode() || !storage.hasValidToken() || storage.isDemoVin()) {
            callback.onError("Gerçek araç VIN'ini ayarlayın ve Togg ID ile giriş yapın.");
            return;
        }
        new TvaApiClient(storage).sendRouteToVehicle(point, new TvaApiClient.DestinationCallback() {
            @Override public void onSuccess(String message) { callback.onSuccess(message); }
            @Override public void onAccepted(String message) { callback.onAccepted(message); }
            @Override public void onUncertain(String message) { callback.onUncertain(message); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public static String buildUniversalGeoUri(double lat, double lon, String name) {
        String uri = "geo:" + lat + "," + lon + "?q=" + lat + "," + lon;
        return name != null && !name.trim().isEmpty() ? uri + "(" + encodeUriComponent(name) + ")" : uri;
    }

    public static boolean openInTrumore(Context context, double lat, double lon, String name) {
        String nativeLink = buildNativeDeepLink(lat, lon, name);
        String betaLink = buildBetaDeepLink(lat, lon, name);
        String shareUrl = buildShareUrl(lat, lon, name);
        Intent[] attempts = {
                new Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl)).setPackage("com.togg.toggmobile"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(nativeLink)).setPackage("com.togg.toggmobile"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl)).setPackage("com.togg.trumore"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(nativeLink)).setPackage("com.togg.trumore"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(betaLink)).setPackage("com.togg.toggmobile"),
                new Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl)),
                new Intent(Intent.ACTION_VIEW, Uri.parse(buildUniversalGeoUri(lat, lon, name)))
        };
        for (Intent intent : attempts) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(intent); return true; } catch (Exception ignored) { }
        }
        return false;
    }

    public static boolean openInTrumore(Context context, GezPoint route) {
        if (route == null) return false;
        if (!route.hasMultipleStops()) return openInTrumore(context, route.lat, route.lon, route.name);
        GezPoint target = route.stops.get(route.stops.size() - 1);
        return openInTrumore(context, target.lat, target.lon, target.name);
    }

    public static String buildNavigationUri(double lat, double lon) { return "google.navigation:q=" + lat + "," + lon; }

    public static String buildNavigationGeoUri(double lat, double lon, String name) {
        return buildUniversalGeoUri(lat, lon, name);
    }

    public static boolean openInNavigation(Context context, double lat, double lon, String name) {
        String[] uris = {buildNavigationUri(lat, lon), buildNavigationGeoUri(lat, lon, name), "https://maps.google.com/?q=" + lat + "," + lon};
        for (String uri : uris) {
            try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
            catch (Exception ignored) { }
        }
        return false;
    }

    public static boolean openInNavigation(Context context, GezPoint route) {
        if (route == null) return false;
        if (!route.hasMultipleStops()) return openInNavigation(context, route.lat, route.lon, route.name);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(buildMultiStopDirectionsUrl(route.stops)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); return true; } catch (Exception ignored) { return false; }
    }
}
