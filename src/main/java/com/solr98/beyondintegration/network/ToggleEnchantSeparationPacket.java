package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：客户端请求切换「主网络」的附魔分离开关，
 * 服务端校验玩家为网络的经理/所有者后取反开关，并回发同步包。
 */
public record ToggleEnchantSeparationPacket() implements CustomPacketPayload {

    public static final Type<ToggleEnchantSeparationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "toggle_enchant_separation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleEnchantSeparationPacket> STREAM_CODEC =
            StreamCodec.unit(new ToggleEnchantSeparationPacket());

    public static void handle(final ToggleEnchantSeparationPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.cannot_toggle_enchant_sep"));
                return;
            }
            if (net instanceof EnchantSeparationAccessor ea) {
                ea.beyond$setEnchantSeparationEnabled(!ea.beyond$isEnchantSeparationEnabled());
                net.setDirty();
                PacketHandler.sendToPlayer(player,
                        new EnchantSeparationSyncPacket(net.getId(), ea.beyond$isEnchantSeparationEnabled()));
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
