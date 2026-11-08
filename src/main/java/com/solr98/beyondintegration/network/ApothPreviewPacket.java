package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.ApothPreviewState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** S2C：神化附魔台三档完整预览附魔列表 + 书架黑名单附魔（容器 id）。 */
public record ApothPreviewPacket(int containerId, List<List<ApothPreviewState.Entry>> previews,
                                 List<String> filtered) implements CustomPacketPayload {

    public static final Type<ApothPreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "apoth_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApothPreviewPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ApothPreviewPacket decode(RegistryFriendlyByteBuf buf) {
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

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ApothPreviewPacket msg) {
                    buf.writeVarInt(msg.containerId());
                    for (List<ApothPreviewState.Entry> list : msg.previews()) {
                        buf.writeVarInt(list.size());
                        for (ApothPreviewState.Entry e : list) {
                            buf.writeVarInt(e.enchantId());
                            buf.writeVarInt(e.level());
                        }
                    }
                    buf.writeVarInt(msg.filtered().size());
                    for (String id : msg.filtered()) {
                        buf.writeUtf(id);
                    }
                }
            };

    public static void handle(final ApothPreviewPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) return;
            ApothPreviewState.set(packet.containerId(), packet.previews());
            ApothPreviewState.setFiltered(packet.containerId(), packet.filtered());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
