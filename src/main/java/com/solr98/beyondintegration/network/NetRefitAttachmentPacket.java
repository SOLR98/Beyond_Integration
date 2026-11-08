package com.solr98.beyondintegration.network;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：从主网络安装配件到主手枪上（换下的旧配件由服务端写回网络）。
 * <p>客户端只传 attachmentId，服务端重新按 ID + 真实类型在网络里查找，避免伪造。
 */
public class NetRefitAttachmentPacket {

    private final AttachmentType type;
    private final ResourceLocation attachmentId;

    public NetRefitAttachmentPacket(AttachmentType type, ResourceLocation attachmentId) {
        this.type = type;
        this.attachmentId = attachmentId;
    }

    public static void encode(NetRefitAttachmentPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        buf.writeResourceLocation(msg.attachmentId);
    }

    public static NetRefitAttachmentPacket decode(FriendlyByteBuf buf) {
        return new NetRefitAttachmentPacket(buf.readEnum(AttachmentType.class), buf.readResourceLocation());
    }

    public static void handle(NetRefitAttachmentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            var net = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            if (!com.solr98.beyondintegration.feature.attachment.NetAttachmentService.install(
                    player, net, msg.type, msg.attachmentId)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.network_attachment.install_failed"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
