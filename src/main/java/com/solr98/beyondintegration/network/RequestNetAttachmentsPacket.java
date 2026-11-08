package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：请求主网络里指定类型（槽位）的配件列表。
 * <p>服务端只按类型筛选；可安装性由客户端按 tacz 口径过滤。镜像可用时客户端不发送本请求。
 */
public record RequestNetAttachmentsPacket(AttachmentType attachmentType, long requestId)
        implements CustomPacketPayload {

    public static final Type<RequestNetAttachmentsPacket> TYPE =
            new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":request_net_attachments"));

    public static final StreamCodec<FriendlyByteBuf, RequestNetAttachmentsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull RequestNetAttachmentsPacket decode(FriendlyByteBuf buf) {
            return new RequestNetAttachmentsPacket(buf.readEnum(AttachmentType.class), buf.readVarLong());
        }

        @Override
        public void encode(FriendlyByteBuf buf, RequestNetAttachmentsPacket packet) {
            buf.writeEnum(packet.attachmentType());
            buf.writeVarLong(packet.requestId());
        }
    };

    public static void handle(final RequestNetAttachmentsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            var list = net == null
                    ? java.util.List.<com.solr98.beyondintegration.feature.attachment.NetAttachmentService.Summary>of()
                    : com.solr98.beyondintegration.feature.attachment.NetAttachmentService.list(
                            net, packet.attachmentType());
            PacketHandler.sendToPlayer(player, NetAttachmentsPacket.ofSummaries(
                    net == null ? -1 : net.getId(), net != null,
                    packet.attachmentType(), packet.requestId(), list));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
