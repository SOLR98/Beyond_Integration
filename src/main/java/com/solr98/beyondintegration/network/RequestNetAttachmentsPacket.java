package com.solr98.beyondintegration.network;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：请求主网络里指定类型（槽位）的配件列表。
 * <p>服务端只按类型筛选（不做可安装性过滤）；是否可安装由客户端按 tacz 原版口径过滤。
 * 镜像可用时客户端不应发送本请求（直接从镜像聚合）。
 */
public class RequestNetAttachmentsPacket {

    private final AttachmentType type;
    /** 请求序号（客户端生成，服务端原样回带；客户端据此丢弃过期响应） */
    private final long requestId;

    public RequestNetAttachmentsPacket(AttachmentType type) {
        this(type, -1L);
    }

    public RequestNetAttachmentsPacket(AttachmentType type, long requestId) {
        this.type = type;
        this.requestId = requestId;
    }

    public static void encode(RequestNetAttachmentsPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        buf.writeVarLong(msg.requestId);
    }

    public static RequestNetAttachmentsPacket decode(FriendlyByteBuf buf) {
        return new RequestNetAttachmentsPacket(buf.readEnum(AttachmentType.class), buf.readVarLong());
    }

    public static void handle(RequestNetAttachmentsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            var net = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getPrimaryNetFromPlayer(player);
            var list = net == null
                    ? java.util.List.<com.solr98.beyondintegration.feature.attachment.NetAttachmentService.Summary>of()
                    : com.solr98.beyondintegration.feature.attachment.NetAttachmentService.list(net, msg.type);
            PacketHandler.sendToPlayer(player, new NetAttachmentsPacket(
                    net == null ? -1 : net.getId(), net != null, msg.type, msg.requestId, list));
        });
        ctx.get().setPacketHandled(true);
    }
}
