package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.SoulBeamClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 灵魂源信标光柱包（S2C）。 */
public record SoulBeamPacket(BlockPos start, int height, int durationTicks,
                             float r, float g, float b, float beamRadius, float glowRadius)
        implements CustomPacketPayload {

    public static final Type<SoulBeamPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":soul_beam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulBeamPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.start());
                        buf.writeVarInt(packet.height());
                        buf.writeVarInt(packet.durationTicks());
                        buf.writeFloat(packet.r());
                        buf.writeFloat(packet.g());
                        buf.writeFloat(packet.b());
                        buf.writeFloat(packet.beamRadius());
                        buf.writeFloat(packet.glowRadius());
                    },
                    buf -> new SoulBeamPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat()));

    public static void handle(final SoulBeamPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                SoulBeamClient.add(packet.start(), packet.height(), packet.durationTicks(),
                        packet.r(), packet.g(), packet.b(), packet.beamRadius(), packet.glowRadius());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
