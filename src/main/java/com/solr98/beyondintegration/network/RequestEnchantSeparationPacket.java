package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S 空包：请求当前网络的附魔分离开关状态，服务端以 EnchantSeparationSyncPacket 应答 */
public record RequestEnchantSeparationPacket() implements CustomPacketPayload {

    public static final Type<RequestEnchantSeparationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_enchant_separation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEnchantSeparationPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestEnchantSeparationPacket());

    public static void handle(final RequestEnchantSeparationPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            boolean enabled = net instanceof EnchantSeparationAccessor ea
                    && ea.beyond$isEnchantSeparationEnabled();
            PacketHandler.sendToPlayer(player, new EnchantSeparationSyncPacket(net.getId(), enabled));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
