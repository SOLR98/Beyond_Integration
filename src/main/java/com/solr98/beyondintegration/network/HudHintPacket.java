package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.HudHintState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * HUD 提示包（S2C）：在快捷栏上方短暂显示一条可翻译文本（如网络图腾消耗提示）。
 * 仅传翻译键与两个长期参数，客户端本地构建文本。
 */
public record HudHintPacket(String translationKey, long arg1, long arg2) implements CustomPacketPayload {

    public static final Type<HudHintPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "hud_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudHintPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public HudHintPacket decode(RegistryFriendlyByteBuf buf) {
                    return new HudHintPacket(buf.readUtf(), buf.readVarLong(), buf.readVarLong());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, HudHintPacket msg) {
                    buf.writeUtf(msg.translationKey());
                    buf.writeVarLong(msg.arg1());
                    buf.writeVarLong(msg.arg2());
                }
            };

    public static void handle(final HudHintPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                HudHintState.show(packet.translationKey(), packet.arg1(), packet.arg2());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
