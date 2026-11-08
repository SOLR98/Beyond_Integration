package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.attachment.NetAttachmentService;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：主网络里指定类型（槽位）的配件列表（服务端只做类型筛选 + 按 attachmentId 去重）。
 * <p>可安装性由客户端过滤；{@code count} = 网络里各 NBT 变种数量之和。
 * <p>协议只带 attachmentId + count，显示用物品栈由客户端按 ID 构造。
 */
public record NetAttachmentsPacket(int netId, boolean hasNetwork, AttachmentType attachmentType,
                                   long requestId, List<Entry> entries) implements CustomPacketPayload {

    /** 单条网络配件：去重后的 attachmentId + 总数量。 */
    public record Entry(ResourceLocation attachmentId, int count) {}

    public static final Type<NetAttachmentsPacket> TYPE =
            new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":net_attachments"));

    public static NetAttachmentsPacket ofSummaries(int netId, boolean hasNetwork, AttachmentType attachmentType,
                                                   long requestId, List<NetAttachmentService.Summary> summaries) {
        List<Entry> list = new ArrayList<>(summaries.size());
        for (NetAttachmentService.Summary summary : summaries) {
            list.add(new Entry(summary.attachmentId(), summary.count()));
        }
        return new NetAttachmentsPacket(netId, hasNetwork, attachmentType, requestId, list);
    }

    public static final StreamCodec<FriendlyByteBuf, NetAttachmentsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull NetAttachmentsPacket decode(FriendlyByteBuf buf) {
            int netId = buf.readVarInt();
            boolean hasNetwork = buf.readBoolean();
            AttachmentType attachmentType = buf.readEnum(AttachmentType.class);
            long requestId = buf.readVarLong();
            int size = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new Entry(buf.readResourceLocation(), buf.readVarInt()));
            }
            return new NetAttachmentsPacket(netId, hasNetwork, attachmentType, requestId, entries);
        }

        @Override
        public void encode(FriendlyByteBuf buf, NetAttachmentsPacket packet) {
            buf.writeVarInt(packet.netId());
            buf.writeBoolean(packet.hasNetwork());
            buf.writeEnum(packet.attachmentType());
            buf.writeVarLong(packet.requestId());
            buf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                buf.writeResourceLocation(entry.attachmentId());
                buf.writeVarInt(entry.count());
            }
        }
    };

    public static void handle(final NetAttachmentsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) return;
            boolean changed = com.solr98.beyondintegration.client.NetAttachmentCache.accept(
                    packet.netId(), packet.hasNetwork(), packet.attachmentType(), packet.requestId(), packet.entries());
            if (!changed) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            var screen = mc.screen;
            if (screen instanceof com.tacz.guns.client.gui.GunRefitScreen refitScreen) {
                mc.execute(() -> {
                    if (mc.screen == refitScreen) refitScreen.init();
                });
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
