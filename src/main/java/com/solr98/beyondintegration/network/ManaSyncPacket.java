package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.ManaState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 网络魔力同步包（S2C）：网络魔力量/上限/是否有源，供客户端 HUD 使用。
 */
public record ManaSyncPacket(long amount, long cap, boolean source) implements CustomPacketPayload {

    public static final Type<ManaSyncPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":mana_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManaSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ManaSyncPacket::amount,
                    ByteBufCodecs.VAR_LONG, ManaSyncPacket::cap,
                    ByteBufCodecs.BOOL, ManaSyncPacket::source,
                    ManaSyncPacket::new);

    public static void handle(final ManaSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ManaState.set(packet.amount(), packet.cap(), packet.source());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
