package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.SoulEnergyState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 网络灵魂量同步包（S2C）：网络灵魂量 + 上限 + 是否有源。 */
public record SoulEnergySyncPacket(long amount, long cap, boolean source) implements CustomPacketPayload {

    public static final Type<SoulEnergySyncPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":soul_energy_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulEnergySyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SoulEnergySyncPacket::amount,
                    ByteBufCodecs.VAR_LONG, SoulEnergySyncPacket::cap,
                    ByteBufCodecs.BOOL, SoulEnergySyncPacket::source,
                    SoulEnergySyncPacket::new);

    public static void handle(final SoulEnergySyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                SoulEnergyState.set(packet.amount(), packet.cap(), packet.source());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
