package com.solr98.beyondintegration.client;

/**
 * 网络灵魂客户端缓存（由 {@code SoulEnergySyncPacket} 更新）。
 * <p>用于在原版灵魂条上叠加"网络魂"那段（方案 B）：HUD 从本缓读取网络魂量与是否有源。
 */
public final class SoulEnergyState {

    private static int amount = 0;
    private static boolean source = false;

    private SoulEnergyState() {
    }

    public static void set(int a, boolean s) {
        amount = Math.max(0, a);
        source = s;
    }

    public static int getAmount() {
        // 条件式：主网络镜像覆盖 SOUL 时优先读镜像（与独立通道值一致，避免双通道）
        var mirrored = com.solr98.beyondintegration.client.mirror.SharedNetData.soul(0);
        if (mirrored.isPresent()) {
            return (int) Math.min(mirrored.getAsLong(), Integer.MAX_VALUE);
        }
        return amount;
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
        source = false;
    }
}
