package com.solr98.beyondintegration.feature.ammo.tacz;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家-网络使用追踪器
 * 记录玩家最近一次消耗弹药所在的网络：短期内（FRESH_TICKS tick 内）
 * 优先返回该网络，过期后回落至玩家主网络，用于决定弹药推送与消耗的归属网络。
 */
public class PlayerNetUsageTracker {

    /** 使用记录：网络 ID + 最近消耗发生的 tick */
    private record Usage(int netId, int lastConsumeTick) {}

    /** 玩家UUID -> 使用记录（并发安全） */
    private static final Map<UUID, Usage> usages = new ConcurrentHashMap<>();
    /** 记录保持"新鲜"的有效 tick 数（过期后回落至主网络） */
    private static final int FRESH_TICKS = 100;

    /**
     * 记录玩家在指定网络消耗弹药
     */
    public static void record(UUID playerUuid, int netId) {
        int tick = ServerLifecycleHooks.getCurrentServer() != null
                ? ServerLifecycleHooks.getCurrentServer().getTickCount() : 0;
        usages.put(playerUuid, new Usage(netId, tick));
    }

    /**
     * 获取玩家当前应使用的网络 ID（新鲜记录优先，否则主网络；无网络返回 -1）
     */
    public static int getCurrentNetId(ServerPlayer player) {
        Usage usage = usages.get(player.getUUID());
        var server = ServerLifecycleHooks.getCurrentServer();
        if (usage != null && server != null && server.getTickCount() - usage.lastConsumeTick() <= FRESH_TICKS) {
            return usage.netId();
        }
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        return primary != null ? primary.getId() : -1;
    }

    /**
     * 移除玩家的使用记录
     */
    public static void remove(UUID playerUuid) {
        usages.remove(playerUuid);
    }

    /**
     * 清空全部使用记录
     */
    public static void clear() {
        usages.clear();
    }
}
