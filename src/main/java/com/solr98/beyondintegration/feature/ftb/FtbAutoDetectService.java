package com.solr98.beyondintegration.feature.ftb;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
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
 * FTB 自动检测服务：BD 网络存储物品变化 → 节流合并 → 对相关在线玩家触发 FTB 任务检测。
 * <p>
 * 触发链路：{@link BdSubscriptionHub} 订阅"使用中的网络"（在线玩家主网络去重）的存储 delta；
 * 仅物品键且与任务目标匹配（{@link FtbTaskNetworkScanner#isRelevant}）的变化才标记该网络为脏；
 * 每 tick 检查节流窗口，到期后清该网络扫描缓存并按 FTB 队伍去重触发 detect
 * （同一队伍只触发一个在线成员，进度由 TeamData 按队伍共享）。
 * <p>
 * 开关与节流参数均为服务器全局配置（auto_detect_enable / auto_detect_throttle_ticks /
 * auto_detect_max_per_tick）；RI 让路或集成关闭时自动退订并清理。
 */
public final class FtbAutoDetectService {

    /** 已订阅网络：netId → 订阅 owner（退订用） */
    private static final Map<Integer, Object> SUBSCRIBED = new ConcurrentHashMap<>();
    /** 脏网络：netId → 首次变化时的服务端 tick */
    private static final Map<Integer, Long> DIRTY = new ConcurrentHashMap<>();
    /** 订阅同步间隔（tick） */
    private static final long SYNC_INTERVAL_TICKS = 100L;

    /** 上次订阅同步 tick（Long.MIN_VALUE = 立即同步） */
    private static volatile long lastSyncTick = Long.MIN_VALUE;
    /** 当前服务端 tick（delta 回调记录脏标记用） */
    private static volatile long currentTick = 0L;

    private FtbAutoDetectService() {}

    /** 玩家登录/登出时请求尽快同步订阅 */
    public static void requestSync() {
        lastSyncTick = Long.MIN_VALUE;
    }

    /** 服务端每 tick（END 阶段）调用 */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        try {
            if (!FtbIntegrationHelper.isEnabled() || !CommandConfig.ftbAutoDetectEnabled()) {
                if (!SUBSCRIBED.isEmpty() || !DIRTY.isEmpty()) clear();
                return;
            }
            currentTick = server.getTickCount();
            if (lastSyncTick == Long.MIN_VALUE || currentTick - lastSyncTick >= SYNC_INTERVAL_TICKS) {
                lastSyncTick = currentTick;
                syncSubscriptions(server);
            }
            processDirty(server);
        } catch (Throwable ignored) {}
    }

    /** 服务器停止/功能关闭时：退订全部并清空本地状态 */
    public static void clear() {
        for (Map.Entry<Integer, Object> e : SUBSCRIBED.entrySet()) {
            try {
                BdSubscriptionHub.unsubscribe(e.getKey(), e.getValue());
            } catch (Throwable ignored) {}
        }
        SUBSCRIBED.clear();
        DIRTY.clear();
        lastSyncTick = Long.MIN_VALUE;
    }

    /** 网络销毁：移除该网络的订阅记录与脏标记（Hub 侧订阅由统一清理处理） */
    public static void onNetDestroyed(int netId) {
        Object owner = SUBSCRIBED.remove(netId);
        if (owner != null) {
            try {
                BdSubscriptionHub.unsubscribe(netId, owner);
            } catch (Throwable ignored) {}
        }
        DIRTY.remove(netId);
    }

    /** 网络存储变化回调：仅物品键且与任务目标匹配时标记脏（回调不捕获 owner/net，保持弱引用语义） */
    private static void onStorageDelta(int netId, Object key, boolean insert) {
        if (!(key instanceof ItemStackKey ik)) return;
        try {
            if (!FtbTaskNetworkScanner.isRelevant(ik.getReadOnlyStack())) return;
        } catch (Throwable ignored) {
            return;
        }
        DIRTY.putIfAbsent(netId, currentTick);
    }

    /** 同步订阅：使用中的网络 = 在线玩家主网络去重 */
    private static void syncSubscriptions(MinecraftServer server) {
        Set<Integer> wanted = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) wanted.add(net.getId());
            } catch (Throwable ignored) {}
        }
        // 新网络订阅
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net == null) continue;
                int netId = net.getId();
                if (SUBSCRIBED.containsKey(netId)) continue;
                Object owner = new Object();
                var handle = BdSubscriptionHub.subscribe(net, owner,
                        (key, size, insert) -> onStorageDelta(netId, key, insert));
                if (handle != null) SUBSCRIBED.put(netId, owner);
            } catch (Throwable ignored) {}
        }
        // 退订不再使用的网络
        Iterator<Map.Entry<Integer, Object>> it = SUBSCRIBED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Object> e = it.next();
            if (!wanted.contains(e.getKey())) {
                try {
                    BdSubscriptionHub.unsubscribe(e.getKey(), e.getValue());
                } catch (Throwable ignored) {}
                it.remove();
                DIRTY.remove(e.getKey());
            }
        }
    }

    /** 处理到期脏网络（单 tick 数量上限，其余顺延到后续 tick） */
    private static void processDirty(MinecraftServer server) {
        int throttle = Math.max(1, CommandConfig.ftbAutoDetectThrottleTicks());
        int maxPerTick = Math.max(1, CommandConfig.ftbAutoDetectMaxPerTick());
        int processed = 0;
        Iterator<Map.Entry<Integer, Long>> it = DIRTY.entrySet().iterator();
        while (it.hasNext() && processed < maxPerTick) {
            Map.Entry<Integer, Long> e = it.next();
            if (currentTick - e.getValue() < throttle) continue;
            int netId = e.getKey();
            it.remove();
            processed++;
            triggerDetect(server, netId);
        }
    }

    /** 清该网络扫描缓存并对相关玩家触发检测（按队伍去重） */
    private static void triggerDetect(MinecraftServer server, int netId) {
        try {
            FtbTaskNetworkScanner.clearCache(netId);
        } catch (Throwable ignored) {}
        Set<UUID> triggeredTeams = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net == null || net.getId() != netId) continue;
                if (!triggeredTeams.add(FtbTeamHelper.teamKey(player))) continue;
                FtbIntegrationHelper.scanTasks(player);
            } catch (Throwable ignored) {}
        }
    }
}
