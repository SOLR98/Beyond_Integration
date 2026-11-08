package com.solr98.beyondintegration.client;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 献祭预览的客户端缓存（按菜单 containerId 存放）。
 *
 * <p>数据由服务端 {@code SacrificePreviewPacket} 下发：
 * 当前四参数、献祭后四参数、是否可献祭及不能献祭的原因键。
 * 供附魔台页面的「献祭」按钮悬浮提示使用。
 */
public final class ApothSacrificePreviewState {

    /**
     * 一条献祭预览。
     *
     * @param ok       是否可献祭
     * @param blockKey 不能献祭时的提示翻译键；可献祭时为 null
     * @param cur      当前 {@code {eterna, quanta, arcana, stable(0|1)}}
     * @param next     献祭后 {@code {eterna, quanta, arcana, stable(0|1)}}
     */
    public record Preview(boolean ok, @Nullable String blockKey, int[] cur, int[] next) {}

    private static final Map<Integer, Preview> CACHE = new ConcurrentHashMap<>();

    private ApothSacrificePreviewState() {}

    public static void set(int containerId, boolean ok, @Nullable String blockKey, int[] cur, int[] next) {
        CACHE.put(containerId, new Preview(ok,
                blockKey == null || blockKey.isEmpty() ? null : blockKey, cur, next));
    }

    /** 取预览；未收到时返回 null（调用方显示「正在读取…」或只显示按钮标题）。 */
    @Nullable
    public static Preview get(int containerId) {
        return CACHE.get(containerId);
    }

    public static void remove(int containerId) {
        CACHE.remove(containerId);
    }

    public static void clear() {
        CACHE.clear();
    }
}
