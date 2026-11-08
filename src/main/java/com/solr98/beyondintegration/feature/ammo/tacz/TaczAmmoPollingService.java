package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczAmmoPushS2CPacket;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TACZ 弹药轮询推送服务：按配置间隔（默认 10 tick）对玩家当前使用的网络
 * 取索引快照（{@code TaczAmmoExtractor.countAllAmmoInNetwork} → {@link TaczAmmoIndex}），
 * 与上次推送快照比较（仅推送层去重），有变化才推送 {@link TaczAmmoPushS2CPacket}。
 *
 * <p>按玩家裁剪：只有当玩家对该网络**有权限**（成员/管理员/所有者）时才推送；
 * 权限丢失时推送清空包（{@code netId=-1}）让客户端丢弃该网络数据。
 * 网络快照本身按网络共享，不做按枪裁剪（流量不敏感，优先保证正确性）。
 *
 * 切枪/换弹事件触发时向玩家即时推送最新快照（绕过轮询延迟）。
 */
public class TaczAmmoPollingService {

    /** 服务端 tick 计数器（按配置间隔触发轮询） */
    private static int tickCounter = 0;

    /** 网络ID -> 上次推送快照（推送层去重基准，非扫桶缓存） */
    private static final Map<Integer, Map<String, Integer>> lastPushedByNet = new ConcurrentHashMap<>();

    /** 玩家 -> 上次收到推送的网络 ID（用于权限变化/切网络时补推或清空） */
    private static final Map<UUID, Integer> lastSentNetByPlayer = new ConcurrentHashMap<>();

    /**
     * 服务端 tick 事件：按配置间隔触发一轮弹药轮询推送
     */
    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (mirrorServesClients() || !CommandConfig.taczAmmoPollEnabled()) {
            // 镜像已覆盖或推送关闭：丢弃脏标记，避免堆积
            TaczAmmoIndex.clearDirty();
            return;
        }
        int interval = CommandConfig.taczAmmoPollIntervalTicks();
        if (interval <= 0) {
            TaczAmmoIndex.clearDirty();
            return;
        }

        tickCounter++;
        if (tickCounter % interval != 0) return;

        // 事件驱动：只处理内容真正变化过的网络（delta/结构性变更置脏）
        Set<Integer> dirtyNets = TaczAmmoIndex.drainDirty();
        if (dirtyNets.isEmpty()) return;
        pollAndPush(server, dirtyNets);
    }

    /**
     * 切枪事件：立即向切枪玩家推送其当前网络的弹药快照（API 索引查询），
     * 使 HUD 在切枪瞬间即有数据并绕过轮询延迟。
     */
    @SubscribeEvent
    public void onGunDraw(GunDrawEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer sp)) return;
        if (mirrorServesClients()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net == null) return;
        pushSnapshotToPlayer(sp, net.getId());
    }

    /**
     * 换弹事件：换弹开始前（canReload 通过后）向玩家推送当前弹药快照，
     * 保证装填期间 HUD 数字实时准确。
     */
    @SubscribeEvent
    public void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer sp)) return;
        if (mirrorServesClients()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net == null) return;
        pushSnapshotToPlayer(sp, net.getId());
    }

    /**
     * 立即推送某网络当前弹药快照给指定玩家（扣弹/切枪/换弹成功后调用，HUD 实时更新）。
     * 无该网络权限时不推送；不更新轮询去重基线。
     */
    public static void pushSnapshotToPlayer(ServerPlayer player, int netId) {
        if (player == null) return;
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return;
        if (!canReceive(player, net)) return;
        Map<String, Integer> snapshot = TaczAmmoExtractor.countAllAmmoInNetwork(net);
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(netId, netName, snapshot));
        lastSentNetByPlayer.put(player.getUUID(), netId);
    }

    /**
     * 镜像是否已完整覆盖客户端所需数据（主网络同步开启且范围含 EXT）。
     * <p>满足时客户端弹药计数由本地镜像聚合（{@code TaczAmmoCache.mirrorAvailable()}），
     * 服务端无需轮询扫描与推送。
     */
    private static boolean mirrorServesClients() {
        if (!CommandConfig.primaryNetSync()) return false;
        return CommandConfig.primaryNetSyncScope().hasExt();
    }

    /** 玩家是否有该网络的访问权限（所有者 / 管理员 / 成员）。 */
    private static boolean canReceive(ServerPlayer player, DimensionsNet net) {
        if (player == null || net == null) return false;
        if (net.isOwner(player.getUUID())) return true;
        return net.getPlayers().contains(player.getUUID());
    }

    /**
     * 处理本轮发生变化的网络：快照按网络共享，推送按玩家定向
     * （仅推"主手是枪 + 当前使用该网络 + 有权限"的玩家；无权限不推，权限丢失发清空包）。
     */
    private void pollAndPush(MinecraftServer server, Set<Integer> dirtyNets) {
        for (int netId : dirtyNets) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                lastPushedByNet.remove(netId);
                continue;
            }
            Map<String, Integer> snapshot = TaczAmmoExtractor.countAllAmmoInNetwork(net);
            boolean changed = !snapshot.equals(lastPushedByNet.get(netId));
            if (changed) {
                lastPushedByNet.put(netId, new HashMap<>(snapshot));
            }

            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerNetUsageTracker.getCurrentNetId(player) != netId) continue;
                UUID uuid = player.getUUID();
                Integer lastNet = lastSentNetByPlayer.get(uuid);
                if (canReceive(player, net)) {
                    if (changed || lastNet == null || lastNet != netId) {
                        PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(netId, netName, snapshot));
                        lastSentNetByPlayer.put(uuid, netId);
                    }
                } else if (lastNet != null && lastNet == netId) {
                    // 权限丢失：通知客户端丢弃该网络缓存
                    PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(-1, "", Map.of()));
                    lastSentNetByPlayer.remove(uuid);
                }
            }
        }
    }

    /**
     * 玩家主网络切换（BD {@code setPrimaryNetForPlayer} / {@code clearPrimaryNetForPlayer} 触发）：
     * 清掉"最近扣弹网络"记录，并按新主网络立即推送；无网络/无权限时发清空包。
     */
    public static void onPrimaryNetChanged(ServerPlayer player) {
        if (player == null) return;
        PlayerNetUsageTracker.remove(player.getUUID());
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null || !canReceive(player, net)) {
            PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(-1, "", Map.of()));
            lastSentNetByPlayer.remove(player.getUUID());
            return;
        }
        pushSnapshotToPlayer(player, net.getId());
    }

    /**
     * 网络被删除：清掉推送基线与"最近扣弹网络"记录，并向当前正在使用该网络的玩家发清空包。
     */
    public static void onNetworkDestroyed(int netId, MinecraftServer server) {
        lastPushedByNet.remove(netId);
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer last = lastSentNetByPlayer.get(player.getUUID());
            boolean using = PlayerNetUsageTracker.getCurrentNetId(player) == netId;
            if (using) {
                PlayerNetUsageTracker.remove(player.getUUID());
            }
            if (last != null && last == netId) {
                PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(-1, "", Map.of()));
                lastSentNetByPlayer.remove(player.getUUID());
            }
        }
    }

    /**
     * 清空推送去重基线（服务器停止时调用）
     */
    public static void clear() {
        lastPushedByNet.clear();
        lastSentNetByPlayer.clear();
    }
}
