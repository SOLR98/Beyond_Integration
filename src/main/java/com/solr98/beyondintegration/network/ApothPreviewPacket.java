package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.ApothPreviewState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C：神化附魔台三档完整预览附魔列表 + 书架黑名单附魔（容器 id）。 */
public class ApothPreviewPacket {

    private final int containerId;
    private final List<List<ApothPreviewState.Entry>> previews;
    private final List<String> filtered;

    public ApothPreviewPacket(int containerId, List<List<ApothPreviewState.Entry>> previews,
                              List<String> filtered) {
        this.containerId = containerId;
        this.previews = previews;
        this.filtered = filtered;
    }

    public static void encode(ApothPreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        for (List<ApothPreviewState.Entry> list : msg.previews) {
            buf.writeVarInt(list.size());
            for (ApothPreviewState.Entry e : list) {
                buf.writeVarInt(e.enchantId());
                buf.writeVarInt(e.level());
            }
        }
        buf.writeVarInt(msg.filtered.size());
        for (String id : msg.filtered) {
            buf.writeUtf(id);
        }
    }

    public static ApothPreviewPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        List<List<ApothPreviewState.Entry>> previews = new ArrayList<>(3);
        for (int slot = 0; slot < 3; slot++) {
            int size = buf.readVarInt();
            List<ApothPreviewState.Entry> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(new ApothPreviewState.Entry(buf.readVarInt(), buf.readVarInt()));
            }
            previews.add(list);
        }
        int filteredSize = buf.readVarInt();
        List<String> filtered = new ArrayList<>(filteredSize);
        for (int i = 0; i < filteredSize; i++) {
            filtered.add(buf.readUtf());
        }
        return new ApothPreviewPacket(containerId, previews, filtered);
    }

    public static void handle(ApothPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ApothPreviewState.set(msg.containerId, msg.previews);
            ApothPreviewState.setFiltered(msg.containerId, msg.filtered);
        }));
        ctx.get().setPacketHandled(true);
    }
}
