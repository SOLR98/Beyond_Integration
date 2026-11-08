package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：从主网络安装配件到主手枪上（换下的旧配件由服务端写回网络）。
 * <p>客户端只传 attachmentId，服务端重新按 ID + 真实类型在网络里查找，避免伪造。
 */
public record NetRefitAttachmentPacket(AttachmentType attachmentType, ResourceLocation attachmentId)
        implements CustomPacketPayload {

    public static final Type<NetRefitAttachmentPacket> TYPE =
            new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":net_refit_attachment"));

    public static final StreamCodec<FriendlyByteBuf, NetRefitAttachmentPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull NetRefitAttachmentPacket decode(FriendlyByteBuf buf) {
            return new NetRefitAttachmentPacket(buf.readEnum(AttachmentType.class), buf.readResourceLocation());
        }

        @Override
        public void encode(FriendlyByteBuf buf, NetRefitAttachmentPacket packet) {
            buf.writeEnum(packet.attachmentType());
            buf.writeResourceLocation(packet.attachmentId());
        }
    };

    public static void handle(final NetRefitAttachmentPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            if (!com.solr98.beyondintegration.feature.attachment.NetAttachmentService.install(
                    player, net, packet.attachmentType(), packet.attachmentId())) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.network_attachment.install_failed"));
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
