package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S 空包：请求当前网络的自动充电开关状态，服务端以 EnergyChargeSyncPacket 应答 */
public record RequestEnergyChargePacket() implements CustomPacketPayload {

    public static final Type<RequestEnergyChargePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_energy_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEnergyChargePacket> STREAM_CODEC =
            StreamCodec.unit(new RequestEnergyChargePacket());

    public static void handle(final RequestEnergyChargePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            boolean enabled = net instanceof EnergyChargeAccessor acc && acc.beyond$isEnergyChargeEnabled();
            PacketHandler.sendToPlayer(player, new EnergyChargeSyncPacket(net.getId(), enabled));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
