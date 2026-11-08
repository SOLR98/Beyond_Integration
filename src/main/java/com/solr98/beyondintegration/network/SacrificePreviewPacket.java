package com.solr98.beyondintegration.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 献祭预览结果（S2C）：当前四参数 + 献祭后四参数 + 可否献祭。
 * <p>四参数均为 {@code {eterna, quanta, arcana, stable(0|1)}}；
 * {@code blockKey} 为空串表示可献祭，否则为不能献祭时的提示翻译键。
 */
public record SacrificePreviewPacket(int containerId, boolean ok, String blockKey,
                                     int[] cur, int[] next) implements CustomPacketPayload {

    public static final Type<SacrificePreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "sacrifice_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SacrificePreviewPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SacrificePreviewPacket decode(RegistryFriendlyByteBuf buf) {
                    int containerId = buf.readVarInt();
                    boolean ok = buf.readBoolean();
                    String blockKey = buf.readUtf();
                    int[] cur = new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
                    int[] next = new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
                    return new SacrificePreviewPacket(containerId, ok, blockKey, cur, next);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SacrificePreviewPacket msg) {
                    buf.writeVarInt(msg.containerId());
                    buf.writeBoolean(msg.ok());
                    buf.writeUtf(msg.blockKey() == null ? "" : msg.blockKey());
                    for (int i = 0; i < 4; i++) buf.writeVarInt(msg.cur()[i]);
                    for (int i = 0; i < 4; i++) buf.writeVarInt(msg.next()[i]);
                }
            };

    public static void handle(final SacrificePreviewPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) return;
            com.solr98.beyondintegration.client.ApothSacrificePreviewState.set(
                    packet.containerId(), packet.ok(), packet.blockKey(), packet.cur(), packet.next());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
