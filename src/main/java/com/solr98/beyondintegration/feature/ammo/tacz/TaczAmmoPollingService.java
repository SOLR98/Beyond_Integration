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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TACZ 弹药轮询推送服务：按配置间隔（默认 10 tick）对玩家当前使用的网络
 * 执行 API 直查（TaczAmmoExtractor.countAllAmmoInNetwork，无服务端缓存），
 * 与上次推送快照比较（仅推送层去重），有变化才推送 TaczAmmoPushS2CPacket。
 *
 * 切枪/换弹事件触发时向玩家即时推送最新快照（绕过轮询延迟）。
 */
public class TaczAmmoPollingService {

    /** 服务端 tick 计数器（按配置间隔触发轮询） */
    private static int tickCounter = 0;

    /** 网络ID -> 上次推送快照（推送层去重基准，非扫桶缓存） */
    private static final Map<Integer, Map<String, Integer>> lastPushedByNet = new ConcurrentHashMap<>();

    /**
     * 服务端 tick 事件：按配置间隔触发一轮弹药轮询推送
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (!CommandConfig.taczAmmoPollEnabled()) return;
        int interval = CommandConfig.taczAmmoPollIntervalTicks();
        if (interval <= 0) return;

        tickCounter++;
        if (tickCounter % interval != 0) return;

        pollAndPush(server);
    }

    /**
     * 切枪事件：立即向切枪玩家推送其主网络的当前弹药快照（API 直查），
     * 使 HUD 在切枪瞬间即有数据并绕过轮询延迟。
     */
    @SubscribeEvent
    public void onGunDraw(GunDrawEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer sp)) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net == null) return;
        pushSnapshotToPlayer(sp, net.getId());
    }

    /**
     * 换弹事件：换弹开始前（canReload 通过后）向玩家推送其主网络当前弹药快照，
     * 保证装填期间 HUD 数字实时准确。
     */
    @SubscribeEvent
    public void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer sp)) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net == null) return;
        pushSnapshotToPlayer(sp, net.getId());
    }

    /**
     * 立即推送某网络当前弹药快照给指定玩家（扣弹/切枪/换弹成功后调用，HUD 实时更新）。
     * 不更新轮询去重基线，轮询仍按原逻辑推送（客户端缓存覆盖式更新，重复推送无副作用）。
     */
    public static void pushSnapshotToPlayer(ServerPlayer player, int netId) {
        if (player == null) return;
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return;
        Map<String, Integer> snapshot = TaczAmmoExtractor.countAllAmmoInNetwork(net);
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(netId, netName, snapshot));
    }

    /**
     * 轮询所有玩家当前使用的网络，API 直查快照并推送（与上次推送一致则不推）；
     * 网络失效时清理去重基线。
     */
    private void pollAndPush(MinecraftServer server) {
        // 收集所有玩家当前使用的网络 ID
        Set<Integer> netIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int netId = PlayerNetUsageTracker.getCurrentNetId(player);
            if (netId >= 0) netIds.add(netId);
        }

        if (netIds.isEmpty()) return;

        // 逐个网络 API 直查，变化时才推送给出使用该网络的玩家
        for (int netId : netIds) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                lastPushedByNet.remove(netId);
                continue;
            }
            Map<String, Integer> snapshot = TaczAmmoExtractor.countAllAmmoInNetwork(net);
            Map<String, Integer> last = lastPushedByNet.get(netId);
            if (snapshot.equals(last)) continue;
            lastPushedByNet.put(netId, new HashMap<>(snapshot));

            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            TaczAmmoPushS2CPacket packet = new TaczAmmoPushS2CPacket(netId, netName, snapshot);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerNetUsageTracker.getCurrentNetId(player) == netId) {
                    PacketHandler.sendToPlayer(player, packet);
                }
            }
        }
    }

    /**
     * 清空推送去重基线（服务器停止时调用）
     */
    public static void clear() {
        lastPushedByNet.clear();
    }
}
