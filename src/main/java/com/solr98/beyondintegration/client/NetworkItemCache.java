package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端网络物品缓存：保存服务端推送的各类物品计数快照，
 * 供界面显示使用，并记录网络连接状态与当前网络 ID。
 */
public class NetworkItemCache {
    /** 物品键 → 数量 缓存表 */
    private static Map<String, Long> counts = new HashMap<>();
    /** 当前是否处于有网络状态 */
    private static boolean hasNetwork = true;
    /** 当前网络 ID（-1 表示无网络） */
    private static int netId = -1;
    /** 缓存版本号（每次全量更新 +1，用于失效判断） */
    private static int version = 0;

    /** 合并增量数据到现有缓存 */
    public static void set(Map<String, Long> data) {
        counts.putAll(data);
    }

    /** 全量替换缓存并更新网络状态与版本号 */
    public static void setAll(Map<String, Long> data, boolean hasNet) {
        counts = new HashMap<>(data);
        hasNetwork = hasNet;
        version++;
    }

    /** 全量替换缓存并更新网络状态、网络 ID 与版本号 */
    public static void setAll(Map<String, Long> data, boolean hasNet, int id) {
        counts = new HashMap<>(data);
        hasNetwork = hasNet;
        netId = id;
        version++;
    }

    /** 获取当前网络 ID */
    public static int getNetId() {
        return netId;
    }

    /** 当前是否有可用网络 */
    public static boolean hasNetwork() {
        return hasNetwork;
    }

    /** 获取指定物品的缓存数量（缺失返回 0） */
    public static long getCount(String itemKey) {
        return counts.getOrDefault(itemKey, 0L);
    }

    /** 获取缓存版本号 */
    public static int getVersion() {
        return version;
    }

    /** 清空全部缓存并复位网络状态 */
    public static void clear() {
        counts.clear();
        hasNetwork = true;
        netId = -1;
    }

    /** 缓存是否为空 */
    public static boolean isEmpty() {
        return counts.isEmpty();
    }
}
