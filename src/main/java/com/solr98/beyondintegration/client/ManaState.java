package com.solr98.beyondintegration.client;

/**
 * 网络魔力客户端缓存（由 {@code ManaSyncPacket} 更新）。
 * <p>供 HUD（法条叠加 / 独立 HUD）读取网络魔力量、上限与是否有源。
 */
public final class ManaState {

    private static long amount = 0L;
    private static long cap = 0L;
    private static boolean source = false;

    private ManaState() {
    }

    public static void set(long a, long c, boolean s) {
        amount = Math.max(0L, a);
        cap = Math.max(0L, c);
        source = s;
    }

    /** 网络魔力量（long）。 */
    public static long getAmount() {
        return amount;
    }

    /** 网络魔力上限；0 = 不限/未设。 */
    public static long getCap() {
        return cap;
    }

    /** 是否有可用的网络魔力源（玩家有主网络且联动启用）。 */
    public static boolean hasSource() {
        return source;
    }

    public static void clear() {
        amount = 0L;
        cap = 0L;
        source = false;
    }
}
