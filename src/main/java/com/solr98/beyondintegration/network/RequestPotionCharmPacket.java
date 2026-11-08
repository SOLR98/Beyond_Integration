package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S 空包：请求当前网络的药水护符开关状态，服务端以 PotionCharmSyncPacket 应答 */
public record RequestPotionCharmPacket() implements CustomPacketPayload {

    public static final Type<RequestPotionCharmPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_potion_charm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPotionCharmPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestPotionCharmPacket());

    public static void handle(final RequestPotionCharmPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            int mode = net instanceof PotionCharmAccessor acc ? acc.beyond$getPotionCharmMode() : 0;
            boolean mendingUnlocked = NetworkAmmoData.getOrCreate(net.getId()).isPotionCharmMending();
            PacketHandler.sendToPlayer(player, new PotionCharmSyncPacket(mode, mendingUnlocked));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
