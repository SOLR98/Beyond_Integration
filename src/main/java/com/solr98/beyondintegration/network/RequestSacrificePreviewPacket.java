package com.solr98.beyondintegration.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 献祭预览请求（C2S 空包）：客户端悬浮「献祭」按钮时请求当前手持书架献祭后的属性变动。
 * <p>预览需要读网络存档（{@code ApothStatData}）并把书架方块的属性扫一遍，
 * 因此必须在服务端算，客户端只负责显示。
 */
public record RequestSacrificePreviewPacket() implements CustomPacketPayload {

    public static final Type<RequestSacrificePreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_sacrifice_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSacrificePreviewPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestSacrificePreviewPacket());

    public static void handle(final RequestSacrificePreviewPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof com.wintercogs.beyonddimensions.common.menu.BDBaseMenu base)) return;
            var widget = base.widget(com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget.class);
            if (widget == null) return;
            var outcome = widget.previewSacrifice();
            PacketDistributor.sendToPlayer(player, new SacrificePreviewPacket(
                    base.containerId, outcome.ok(), outcome.blockKey(), outcome.cur(), outcome.next()));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
