package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SyncConfigSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端：主网络同步配置推送器。
 * <p>玩家登录、以及配置重载时，将 {@code primary_net_sync / primary_net_sync_scope}
 * 推送给客户端。与 JEI 是否安装无关。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NetworkSyncConfigPusher {

    private NetworkSyncConfigPusher() {}

    /** 玩家登录：推送一次配置（早于首个数据包）。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            push(player);
        }
    }

    /** 向单个玩家推送当前配置。 */
    public static void push(ServerPlayer player) {
        if (player == null) return;
        com.solr98.beyondintegration.core.sync.NetSyncDebug.log(
                "push config player={} enabled={} scope={}",
                player.getUUID(), CommandConfig.primaryNetSync(), CommandConfig.primaryNetSyncScope());
        PacketHandler.sendToPlayer(player, new SyncConfigSyncPacket(
                CommandConfig.primaryNetSync(),
                CommandConfig.primaryNetSyncScope().ordinal()));
    }

    /** 向全部在线玩家推送（配置重载时调用）。 */
    public static void pushAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            push(player);
        }
    }
}
