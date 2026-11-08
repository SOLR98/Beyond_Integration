package com.solr98.beyondintegration.client;

/**
 * 网络灵魂客户端缓存（由 {@code SoulEnergySyncPacket} 更新）。
 */
public final class SoulEnergyState {

    private static long amount = 0L;
    private static long max = 0L;
    private static boolean source = false;

    private SoulEnergyState() {
    }

    public static void set(long a, long m, boolean s) {
        long newAmount = Math.max(0L, a);
        if (source && s && amount != newAmount) {
            com.solr98.beyondintegration.client.hud.SoulChangePopups.pushNetworkDelta(amount, newAmount);
        } else if (!s) {
            com.solr98.beyondintegration.client.hud.SoulChangePopups.clearNetwork();
        }
        amount = newAmount;
        max = Math.max(0L, m);
        source = s;
    }

    /** 网络魂量（long；条件式优先读主网络镜像）。 */
    public static long getAmount() {
        // 条件式：主网络镜像覆盖 SOUL 时优先读镜像（与独立通道值一致，避免双通道）
        var mirrored = com.solr98.beyondintegration.client.mirror.SharedNetData.soul(0);
        if (mirrored.isPresent()) {
            return Math.max(0L, mirrored.getAsLong());
        }
        return amount;
    }

    /** HUD 灵魂条分母用的网络魂上限；0 = 不限/未设（HUD 回退原版 MaxArcaSouls）。 */
    public static long getMax() {
        return max;
    }

    public static boolean hasSource() {
        if (com.solr98.beyondintegration.client.mirror.SharedNetData
                .available(com.solr98.beyondintegration.core.sync.NetDataType.SOUL)) {
            return getAmount() > 0;
        }
        return source && amount > 0;
    }

    public static void clear() {
        amount = 0;
        max = 0L;
        source = false;
        com.solr98.beyondintegration.client.hud.SoulChangePopups.clear();
    }
}
