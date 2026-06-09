package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ToggleEnchantSeparationPacket() implements CustomPacketPayload {
    public static final Type<ToggleEnchantSeparationPacket> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":toggle_enchant_separation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleEnchantSeparationPacket> STREAM_CODEC = StreamCodec.unit(new ToggleEnchantSeparationPacket());

    public static void handle(final ToggleEnchantSeparationPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null || !(net instanceof EnchantSeparationAccessor acc)) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable("message.beyond_integration.cannot_toggle_enchant_sep"));
                return;
            }
            acc.beyond$setEnchantSeparationEnabled(!acc.beyond$isEnchantSeparationEnabled());
            net.setDirty();
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
