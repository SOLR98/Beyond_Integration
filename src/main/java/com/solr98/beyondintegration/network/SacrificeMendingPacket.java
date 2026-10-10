package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.feature.charm.NetworkPotionCharmHandler;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端请求为"主网络"献祭一本"单条附魔=经验修补"的附魔书，
 * 服务端校验经理/所有者权限后扣除该书并持久化解锁该网络的经验修补，随后同步状态。
 */
public class SacrificeMendingPacket {

    /** 空构造：本包无字段 */
    public SacrificeMendingPacket() {}

    public static void encode(SacrificeMendingPacket msg, FriendlyByteBuf buf) {}

    public static SacrificeMendingPacket decode(FriendlyByteBuf buf) {
        return new SacrificeMendingPacket();
    }

    public static void handle(SacrificeMendingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.cannot_toggle_potion_charm"));
                return;
            }
            // 已解锁：Shift+点击失效（不再献祭、不提示），仅回同步当前状态
            boolean already = NetworkAmmoData.getOrCreate(net.getId()).isPotionCharmMending();
            if (!already) {
                boolean ok = NetworkPotionCharmHandler.sacrificeMending(net);
                player.sendSystemMessage(Component.translatable(ok
                        ? "message.beyond_integration.potion_charm_sacrifice_ok"
                        : "message.beyond_integration.potion_charm_sacrifice_fail"));
            }
            int mode = net instanceof PotionCharmAccessor acc ? acc.beyond$getPotionCharmMode() : 0;
            PacketHandler.sendToPlayer(player, new PotionCharmSyncPacket(mode,
                    NetworkAmmoData.getOrCreate(net.getId()).isPotionCharmMending()));
        });
        ctx.get().setPacketHandled(true);
    }
}
