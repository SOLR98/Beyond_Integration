package com.solr98.beyondintegration.feature.ftb;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.network.FtbTaskNetworkCountResponsePacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FTB 任务 tooltip 网络数量推送服务（服务端）：
 * <p>
 * 客户端悬停任务时请求计数（同时登记订阅）；BD 网络相关物品变化时，经节流窗口
 * 主动向订阅玩家推送最新数量（复用 {@code FtbTaskNetworkCountResponsePacket}），
 * 实现"网络变化后立即更新"（默认 10 tick ≈ 0.5 秒，可配置）。
 * <p>
 * 无人悬停时零开销；订阅 10 秒无请求自动清理，玩家登出/停服时清理。
 */
public final class FtbTooltipPushService {

    /** 订阅：玩家 → (任务 ID → 最后请求 tick) */
    private static final Map<UUID, Map<Long, Long>> SUBSCRIPTIONS = new ConcurrentHashMap<>();
    /** 脏网络：netId → 首次变化 tick */
    private static final Map<Integer, Long> DIRTY_NETS = new ConcurrentHashMap<>();
    /** 已订阅网络：netId → 订阅 owner（退订用） */
    private static final Map<Integer, Object> NET_OWNERS = new ConcurrentHashMap<>();

    /** 订阅有效期（逻辑刻，10 秒无请求自动清理） */
    private static final long SUBSCRIPTION_TTL_TICKS = 200L;
    /** 清理间隔（逻辑刻） */
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    private static volatile long currentTick = 0L;
    private static volatile long lastCleanupTick = Long.MIN_VALUE;

    private FtbTooltipPushService() {}

    /** 客户端请求计数时登记/续订订阅（请求包处理调用） */
    public static void onRequest(ServerPlayer player, long taskId) {
        if (player == null) return;
        try {
            if (!FtbIntegrationHelper.isEnabled()) return;
            SUBSCRIPTIONS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).put(taskId, currentTick);
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null) ensureSubscription(net.getId(), net);
        } catch (Throwable ignored) {}
    }

    /** 玩家登出：清理订阅（网络订阅由清理周期/停服统一退订） */
    public static void onPlayerLoggedOut(UUID playerId) {
        if (playerId != null) SUBSCRIPTIONS.remove(playerId);
    }

    /** 服务器停止/功能关闭：退订并清空状态 */
    public static void clear() {
        for (Map.Entry<Integer, Object> e : NET_OWNERS.entrySet()) {
            try {
                BdSubscriptionHub.unsubscribe(e.getKey(), e.getValue());
            } catch (Throwable ignored) {}
        }
        NET_OWNERS.clear();
        DIRTY_NETS.clear();
        SUBSCRIPTIONS.clear();
        lastCleanupTick = Long.MIN_VALUE;
    }

    /** 服务端每 tick（Post 阶段）：处理脏网络推送与订阅清理 */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        try {
            if (!FtbIntegrationHelper.isEnabled()) {
                if (!SUBSCRIPTIONS.isEmpty() || !NET_OWNERS.isEmpty()) clear();
                return;
            }
            currentTick = server.getTickCount();
            processDirty(server);
            if (lastCleanupTick == Long.MIN_VALUE || currentTick - lastCleanupTick >= CLEANUP_INTERVAL_TICKS) {
                lastCleanupTick = currentTick;
                cleanupSubscriptions(server);
            }
        } catch (Throwable ignored) {}
    }

    /** 网络存储变化回调：仅任务目标物品相关变化时标记脏（回调不捕获 owner/net，保持弱引用语义） */
    private static void onStorageDelta(int netId, Object key, boolean insert) {
        if (!(key instanceof ItemStackKey ik)) return;
        try {
            if (!FtbTaskNetworkScanner.isRelevant(ik.getReadOnlyStack())) return;
        } catch (Throwable ignored) {
            return;
        }
        DIRTY_NETS.putIfAbsent(netId, currentTick);
    }

    /** 确保对该网络订阅存储变化 */
    private static void ensureSubscription(int netId, DimensionsNet net) {
        if (NET_OWNERS.containsKey(netId)) return;
        Object owner = new Object();
        var handle = BdSubscriptionHub.subscribe(net, owner,
                (key, size, insert) -> onStorageDelta(netId, key, insert));
        if (handle != null) NET_OWNERS.put(netId, owner);
    }

    /** 处理到期脏网络：对主网络 = 该网络的订阅玩家重算并推送（节流窗口） */
    private static void processDirty(MinecraftServer server) {
        if (DIRTY_NETS.isEmpty()) return;
        int throttle = Math.max(1, CommandConfig.ftbTooltipPushThrottleTicks());
        Iterator<Map.Entry<Integer, Long>> it = DIRTY_NETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            if (currentTick - e.getValue() < throttle) continue;
            int netId = e.getKey();
            it.remove();
            pushToSubscribers(server, netId);
        }
    }

    /** 对"主网络 = netId"的订阅玩家重算其订阅任务并推送 */
    private static void pushToSubscribers(MinecraftServer server, int netId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                Map<Long, Long> tasks = SUBSCRIPTIONS.get(player.getUUID());
                if (tasks == null || tasks.isEmpty()) continue;
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net == null || net.getId() != netId) continue;
                for (Long taskId : new HashSet<>(tasks.keySet())) {
                    FtbTaskNetworkScanner.TaskNetworkInfo info =
                            FtbIntegrationHelper.taskNetworkInfo(player, taskId);
                    if (info == null) {
                        PacketHandler.sendToPlayer(player,
                                new FtbTaskNetworkCountResponsePacket(taskId, -1, -1, ""));
                    } else {
                        PacketHandler.sendToPlayer(player,
                                new FtbTaskNetworkCountResponsePacket(
                                        taskId, info.count(), info.netId(), info.netName()));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /** 清理过期订阅；退订不再有订阅者的网络 */
    private static void cleanupSubscriptions(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Map<Long, Long>>> playerIt = SUBSCRIPTIONS.entrySet().iterator();
        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<Long, Long>> pe = playerIt.next();
            Map<Long, Long> tasks = pe.getValue();
            tasks.entrySet().removeIf(t -> currentTick - t.getValue() > SUBSCRIPTION_TTL_TICKS);
            if (tasks.isEmpty()) playerIt.remove();
        }
        Set<Integer> activeNets = new HashSet<>();
        for (UUID uuid : SUBSCRIPTIONS.keySet()) {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) continue;
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) activeNets.add(net.getId());
            } catch (Throwable ignored) {}
        }
        Iterator<Map.Entry<Integer, Object>> netIt = NET_OWNERS.entrySet().iterator();
        while (netIt.hasNext()) {
            Map.Entry<Integer, Object> ne = netIt.next();
            if (!activeNets.contains(ne.getKey())) {
                try {
                    BdSubscriptionHub.unsubscribe(ne.getKey(), ne.getValue());
                } catch (Throwable ignored) {}
                netIt.remove();
                DIRTY_NETS.remove(ne.getKey());
            }
        }
    }
}
