package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.attachment.NetAttachmentService;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C：主网络里指定类型（槽位）的配件列表（服务端只做类型筛选 + 按 attachmentId 去重）。
 * <p>可安装性由客户端按 tacz 原版口径过滤（{@code allowAttachmentType}/{@code allowAttachment}）；
 * {@code count} = 网络里各 NBT 变种数量之和。
 */
public class NetAttachmentsPacket {

    /** 单条网络配件：去重后的 attachmentId、总数量、显示用物品栈 */
    public record Entry(ResourceLocation attachmentId, int count, ItemStack display) {}

    private final int netId;
    private final boolean hasNetwork;
    private final AttachmentType type;
    /** 请求序号：>=0 = 请求响应（客户端据此丢弃过期响应）；-1 = 服务端主动推送 */
    private final long requestId;
    private final List<Entry> entries;

    public NetAttachmentsPacket(int netId, boolean hasNetwork, AttachmentType type,
                                List<NetAttachmentService.Summary> summaries) {
        this(netId, hasNetwork, type, -1L, summaries);
    }

    public NetAttachmentsPacket(int netId, boolean hasNetwork, AttachmentType type, long requestId,
                                List<NetAttachmentService.Summary> summaries) {
        this.netId = netId;
        this.hasNetwork = hasNetwork;
        this.type = type;
        this.requestId = requestId;
        List<Entry> list = new ArrayList<>(summaries.size());
        for (NetAttachmentService.Summary summary : summaries) {
            list.add(new Entry(summary.attachmentId(), summary.count(), summary.display()));
        }
        this.entries = list;
    }

    public NetAttachmentsPacket(int netId, boolean hasNetwork, AttachmentType type, long requestId,
                                List<Entry> entries, boolean raw) {
        this.netId = netId;
        this.hasNetwork = hasNetwork;
        this.type = type;
        this.requestId = requestId;
        this.entries = entries;
    }

    public static void encode(NetAttachmentsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeBoolean(msg.hasNetwork);
        buf.writeEnum(msg.type);
        buf.writeVarLong(msg.requestId);
        buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) {
            buf.writeResourceLocation(entry.attachmentId());
            buf.writeVarInt(entry.count());
            buf.writeItem(entry.display());
        }
    }

    public static NetAttachmentsPacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        boolean hasNetwork = buf.readBoolean();
        AttachmentType type = buf.readEnum(AttachmentType.class);
        long requestId = buf.readVarLong();
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int count = buf.readVarInt();
            ItemStack display = buf.readItem();
            entries.add(new Entry(id, count, display));
        }
        return new NetAttachmentsPacket(netId, hasNetwork, type, requestId, entries, true);
    }

    public static void handle(NetAttachmentsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!ctx.get().getDirection().getReceptionSide().isClient()) return;
            boolean changed = com.solr98.beyondintegration.client.NetAttachmentCache.accept(
                    msg.netId, msg.hasNetwork, msg.type, msg.requestId, msg.entries);
            if (!changed) return;
            // 显示层重建与执行分离：仅在列表内容真正变化时，于主线程重建一次改装界面
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            var screen = mc.screen;
            if (screen instanceof com.tacz.guns.client.gui.GunRefitScreen refitScreen) {
                mc.execute(() -> {
                    if (mc.screen == refitScreen) refitScreen.init();
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
