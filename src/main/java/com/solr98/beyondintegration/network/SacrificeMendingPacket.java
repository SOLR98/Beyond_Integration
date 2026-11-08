package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.feature.charm.NetworkPotionCharmHandler;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：客户端请求为「主网络」献祭一本「单条附魔=经验修补」的附魔书，
 * 服务端校验经理/所有者权限后扣除该书并持久化解锁该网络的经验修补，随后同步状态。
 */
public record SacrificeMendingPacket() implements CustomPacketPayload {

    public static final Type<SacrificeMendingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "sacrifice_mending"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SacrificeMendingPacket> STREAM_CODEC =
            StreamCodec.unit(new SacrificeMendingPacket());

    public static void handle(final SacrificeMendingPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
