package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget;
import com.solr98.beyondintegration.feature.enchant.EnchantTableWidget;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：请求当前神化附魔台工作台的完整预览附魔列表。 */
public record RequestApothPreviewPacket() implements CustomPacketPayload {

    public static final Type<RequestApothPreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_apoth_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestApothPreviewPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestApothPreviewPacket());

    public static void handle(final RequestApothPreviewPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof BDBaseMenu base) {
                ApothEnchantTableWidget apoth = base.widget(ApothEnchantTableWidget.class);
                if (apoth != null) {
                    apoth.sendPreview(player);
                    return;
                }
                EnchantTableWidget vanilla = base.widget(EnchantTableWidget.class);
                if (vanilla != null) vanilla.sendPreview(player);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
