package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 神化附魔台“完整预览附魔列表”的客户端缓存（按容器 id 存 3 档）。
 * <p>服务端 {@code ApothEnchantTableMenu} 计算每档全部附魔，经 {@code ApothPreviewPacket} 下发。
 */
public final class ApothPreviewState {

    /** 单个预览附魔：附魔 id + 等级。 */
    public record Entry(int enchantId, int level) {
    }

    private static final Map<Integer, List<List<Entry>>> CACHE = new HashMap<>();
    private static final Map<Integer, List<String>> FILTERED = new HashMap<>();

    private ApothPreviewState() {
    }

    public static void set(int containerId, List<List<Entry>> previews) {
        CACHE.put(containerId, previews);
    }

    public static List<List<Entry>> get(int containerId) {
        return CACHE.getOrDefault(containerId, List.of());
    }

    public static void setFiltered(int containerId, List<String> ids) {
        FILTERED.put(containerId, ids);
    }

    /** 书架黑名单附魔 ID（来自献祭的书架）。 */
    public static List<String> filtered(int containerId) {
        return FILTERED.getOrDefault(containerId, List.of());
    }

    public static void clear() {
        CACHE.clear();
        FILTERED.clear();
    }
}
