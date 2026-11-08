package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.charm.NetworkPotionCharmHandler;
import com.solr98.beyondintegration.feature.charm.PotionCharmMode;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：客户端正常点击按钮，请求把「主网络」的网络药水护符生效目标切换到下一个
 * （仅玩家 → 仅女仆 → 玩家和女仆 → 关闭 → …）。服务端校验经理/所有者权限后切换并同步。
 */
public record CyclePotionCharmModePacket() implements CustomPacketPayload {

    public static final Type<CyclePotionCharmModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "cycle_potion_charm_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CyclePotionCharmModePacket> STREAM_CODEC =
            StreamCodec.unit(new CyclePotionCharmModePacket());

    public static void handle(final CyclePotionCharmModePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
