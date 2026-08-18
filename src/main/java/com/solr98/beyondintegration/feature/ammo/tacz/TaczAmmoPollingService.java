package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczAmmoPushS2CPacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TACZ 弹药轮询推送服务：按配置间隔（默认 10 tick）轮询玩家当前使用的网络，
 * 读取 TaczAmmoTracker 查询缓存快照（增量维护，无变化不推），
 * 推送 TaczAmmoPushS2CPacket 给使用该网络的玩家，并回收闲置追踪器。
 */
public class TaczAmmoPollingService {

    /** 服务端 tick 计数器（按配置间隔触发轮询） */
    private static int tickCounter = 0;

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
     * 轮询所有玩家当前使用的网络，读取缓存快照并推送数据包（无变化不推）；
     * 顺带回收闲置（超 5 分钟）的追踪器。
     */
    private void pollAndPush(MinecraftServer server) {
        // 收集所有玩家当前使用的网络 ID
        Set<Integer> netIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int netId = PlayerNetUsageTracker.getCurrentNetId(player);
            if (netId >= 0) netIds.add(netId);
        }

        // 闲置回收（在用网络之外、超 5 分钟的追踪器，下次使用重建）
        TaczAmmoTracker.reapIdle(netIds);

        if (netIds.isEmpty()) return;

        // 逐个网络读取缓存快照，变化时才推送给出使用该网络的玩家
        for (int netId : netIds) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                // 网络已失效，清理对应追踪器
                TaczAmmoTracker.removeById(netId);
                continue;
            }
            TaczAmmoTracker tracker = TaczAmmoTracker.getOrCreate(net);
            if (!tracker.hasChangedSinceLastPush()) continue;
            Map<String, Integer> snapshot = tracker.getAllCounts();
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            TaczAmmoPushS2CPacket packet = new TaczAmmoPushS2CPacket(netId, netName, snapshot);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerNetUsageTracker.getCurrentNetId(player) == netId) {
                    PacketHandler.sendToPlayer(player, packet);
                }
            }
        }
    }
}
