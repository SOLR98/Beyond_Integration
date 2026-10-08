package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.HudHintState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * HUD 提示包（S2C，NeoForge）：在快捷栏上方短暂显示一条可翻译文本（如网络图腾消耗提示）。
 */
public record HudHintPayload(String translationKey, long arg1, long arg2) implements CustomPacketPayload {

    public static final Type<HudHintPayload> TYPE =
            new Type<>(ResourceLocation.parse("beyond_integration:hud_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudHintPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull HudHintPayload decode(RegistryFriendlyByteBuf buf) {
            return new HudHintPayload(buf.readUtf(), buf.readVarLong(), buf.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, HudHintPayload payload) {
            buf.writeUtf(payload.translationKey);
            buf.writeVarLong(payload.arg1);
            buf.writeVarLong(payload.arg2);
        }
    };

    public static void handle(final HudHintPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> HudHintState.show(payload.translationKey, payload.arg1, payload.arg2));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
