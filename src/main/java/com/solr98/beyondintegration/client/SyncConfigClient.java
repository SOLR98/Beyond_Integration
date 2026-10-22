package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope;

import java.util.Map;

/**
 * 客户端「服务端配置」缓存（由 {@code SyncConfigSyncPayload} 整包更新）。
 * <p>以服务端推送的权威值为准，供 UI / 诊断 / 客户端条件逻辑使用。
 */
public final class SyncConfigClient {

    private static volatile boolean enabled = false;
    private static volatile PrimaryNetSyncScope scope = PrimaryNetSyncScope.ITEMS;
    /** 全部服务端配置快照（路径 → 字符串值）。 */
    private static volatile Map<String, String> all = Map.of();

    private SyncConfigClient() {}

    public static void set(boolean e, PrimaryNetSyncScope s, Map<String, String> allConfig) {
        enabled = e;
        scope = s == null ? PrimaryNetSyncScope.ITEMS : s;
        all = allConfig == null ? Map.of() : Map.copyOf(allConfig);
        com.solr98.beyondintegration.core.sync.NetSyncDebug.log(
                "client config: enabled={} scope={} entries={}", enabled, scope, all.size());
    }

    public static boolean isActive() {
        return enabled;
    }

    public static PrimaryNetSyncScope scope() {
        return scope;
    }

    /** 全部服务端配置快照（路径 → 字符串值）。 */
    public static Map<String, String> all() {
        return all;
    }

    public static String getString(String path, String def) {
        String v = all.get(path);
        return v == null ? def : v;
    }

    public static boolean getBoolean(String path, boolean def) {
        String v = all.get(path);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    public static int getInt(String path, int def) {
        String v = all.get(path);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static long getLong(String path, long def) {
        String v = all.get(path);
        if (v == null) return def;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static double getDouble(String path, double def) {
        String v = all.get(path);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static void reset() {
        enabled = false;
        scope = PrimaryNetSyncScope.ITEMS;
        all = Map.of();
    }
}
