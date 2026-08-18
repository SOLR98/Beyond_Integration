package com.solr98.beyondintegration.feature.ammo.sw;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoDeltaS2CPacket;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;

/**
 * SW 固定项轮询对账服务：按配置间隔（默认 10 tick）轮询所有在用网络，
 * 经 SwAmmoTracker 差分产出全量/增量后推送给主网络为该网络的玩家。
 */
public class SwAmmoPollingService {

    /** 服务端 tick 计数器（按配置间隔触发轮询） */
    private static int tickCounter = 0;

    /**
     * 服务端 tick 事件：按配置间隔触发一轮弹药轮询对账
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (!CommandConfig.swAmmoPollEnabled()) return;
        int interval = CommandConfig.swAmmoPollIntervalTicks();
        if (interval <= 0) return;

        tickCounter++;
        if (tickCounter % interval != 0) return;

        pollAndPush(server);
    }

    /**
     * 轮询所有玩家在用网络，经 SwAmmoTracker 差分后向主网络为该网络的玩家
     * 推送全量（SuperbAmmoStatusResponsePacket）或增量（SuperbAmmoDeltaS2CPacket）数据包
     */
    private void pollAndPush(MinecraftServer server) {
        // 收集所有在线玩家主网络 ID
        Set<Integer> netIds = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary != null) netIds.add(primary.getId());
        }
        if (netIds.isEmpty()) return;

        // 逐个网络对账并推送
        for (int netId : netIds) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net == null) {
                // 网络已失效，清理对应追踪器
                SwAmmoTracker.removeById(netId);
                continue;
            }

            SwAmmoTracker tracker = SwAmmoTracker.getOrCreate(net);
            SwAmmoTracker.DeltaResult result = tracker.drain(net);
            if (result == null) continue;

            // 仅推送给出主网络为该网络的玩家
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (primary == null || primary.getId() != netId) continue;

                // 全量/增量分别走不同数据包
                if (result.full()) {
                    PacketHandler.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                            netId, result.netName(), result.energy(), result.enchantSeparation(),
                            result.ammo(), null));
                } else {
                    PacketHandler.sendToPlayer(player, new SuperbAmmoDeltaS2CPacket(
                            netId, false, false, result.ammo(), result.energy(),
                            result.netName(), result.enchantSeparation()));
                }
            }
        }
    }
}
