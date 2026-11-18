package com.togg.companion.core;
import java.util.*;
/** Shared card catalog/order and row packing; wide cards retain their saved position. */
public final class HomeCardLayout {
    public static final String DEFAULT_ORDER = "odometer,battery,drive,tires,location,12v,abrp";
    private static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(DEFAULT_ORDER.split(",")));
    private HomeCardLayout() { }
    private static LinkedHashSet<String> known(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value != null) for (String item : value.split(",")) { String key = item.trim(); if (KEYS.contains(key)) result.add(key); }
        return result;
    }
    public static String order(String value) { LinkedHashSet<String> keys = known(value); keys.addAll(KEYS); return String.join(",", keys); }
    public static String visible(String value) { return String.join(",", known(value)); }
    public static String upgradeVisible(String oldOrder, String oldVisible) {
        Set<String> previous = known(oldOrder); LinkedHashSet<String> visible = known(oldVisible);
        for (String key : KEYS) if (!previous.contains(key)) visible.add(key);
        return String.join(",", visible);
    }
    public static boolean fullWidth(String key) { return "location".equals(key) || "odometer".equals(key) || "abrp".equals(key); }
    public static List<List<String>> rows(String savedOrder, String savedVisible, int columns) {
        int width = Math.max(1, columns); Set<String> visible = known(savedVisible);
        List<List<String>> result = new ArrayList<>(); List<String> pending = new ArrayList<>();
        for (String key : order(savedOrder).split(",")) {
            if (!visible.contains(key)) continue;
            if (fullWidth(key)) {
                if (!pending.isEmpty()) { result.add(new ArrayList<>(pending)); pending.clear(); }
                result.add(Collections.singletonList(key));
            } else {
                pending.add(key);
                if (pending.size() == width) { result.add(new ArrayList<>(pending)); pending.clear(); }
            }
        }
        if (!pending.isEmpty()) result.add(pending);
        return result;
    }
}
