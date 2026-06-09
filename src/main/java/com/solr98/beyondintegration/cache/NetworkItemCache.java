package com.solr98.beyondintegration.cache;

import java.util.HashMap;
import java.util.Map;

public class NetworkItemCache {
    private static Map<String, Long> counts = new HashMap<>();
    private static boolean hasNetwork = true;
    private static int netId = -1;
    private static int version = 0;

    public static void set(Map<String, Long> data) {
        counts.putAll(data);
    }

    public static void setAll(Map<String, Long> data, boolean hasNet) {
        counts = new HashMap<>(data);
        hasNetwork = hasNet;
        version++;
    }

    public static void setAll(Map<String, Long> data, boolean hasNet, int id) {
        counts = new HashMap<>(data);
        hasNetwork = hasNet;
        netId = id;
        version++;
    }

    public static int getNetId() {
        return netId;
    }

    public static boolean hasNetwork() {
        return hasNetwork;
    }

    public static long getCount(String itemKey) {
        return counts.getOrDefault(itemKey, 0L);
    }

    public static int getVersion() {
        return version;
    }

    public static void clear() {
        counts.clear();
        hasNetwork = true;
        netId = -1;
    }

    public static boolean isEmpty() {
        return counts.isEmpty();
    }
}
