package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.feature.charm.PotionCharmMode;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端正常点击按钮，请求把"主网络"的网络药水护符生效目标切换到下一个
 * （仅玩家 → 仅女仆 → 玩家和女仆 → 关闭 → …）。服务端校验经理/所有者权限后切换并同步。
 */
public class CyclePotionCharmModePacket {

    public CyclePotionCharmModePacket() {}

    public static void encode(CyclePotionCharmModePacket msg, FriendlyByteBuf buf) {}

    public static CyclePotionCharmModePacket decode(FriendlyByteBuf buf) {
        return new CyclePotionCharmModePacket();
    }

    public static void handle(CyclePotionCharmModePacket msg, Supplier<NetworkEvent.Context> ctx) {
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
            if (net instanceof PotionCharmAccessor acc) {
                int next = PotionCharmMode.of(acc.beyond$getPotionCharmMode()).next().ordinal();
                acc.beyond$setPotionCharmMode(next);
                net.setDirty();
                PacketHandler.sendToPlayer(player, new PotionCharmSyncPacket(next,
                        NetworkAmmoData.getOrCreate(net.getId()).isPotionCharmMending()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
