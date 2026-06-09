package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.cache.NetworkItemCache;
import com.solr98.beyondintegration.handler.ToastDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class NetworkItemCountsPacket {

    private final Map<String, Long> counts;
    private final boolean replace;
    private final boolean hasNetwork;
    private final int netId;
    private final ItemStack resultItem;
    private final int resultCount;

    public NetworkItemCountsPacket(Map<String, Long> counts) {
        this(counts, false, true, -1, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace) {
        this(counts, replace, true, -1, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork) {
        this(counts, replace, hasNetwork, -1, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork, int netId) {
        this(counts, replace, hasNetwork, netId, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork, int netId,
                                    ItemStack resultItem, int resultCount) {
        this.counts = counts;
        this.replace = replace;
        this.hasNetwork = hasNetwork;
        this.netId = netId;
        this.resultItem = resultItem;
        this.resultCount = resultCount;
    }

    public static void encode(NetworkItemCountsPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.replace);
        buf.writeBoolean(msg.hasNetwork);
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.counts.size());
        for (var e : msg.counts.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarLong(e.getValue());
        }
        boolean hasResult = !msg.resultItem.isEmpty();
        buf.writeBoolean(hasResult);
        if (hasResult) {
            buf.writeItemStack(msg.resultItem, true);
            buf.writeVarInt(msg.resultCount);
        }
    }

    public static NetworkItemCountsPacket decode(FriendlyByteBuf buf) {
        boolean replace = buf.readBoolean();
        boolean hasNetwork = buf.readBoolean();
        int netId = buf.readVarInt();
        int size = buf.readVarInt();
        Map<String, Long> counts = new HashMap<>();
        for (int i = 0; i < size; i++) {
            counts.put(buf.readUtf(), buf.readVarLong());
        }
        boolean hasResult = buf.readBoolean();
        ItemStack resultItem = hasResult ? buf.readItem() : ItemStack.EMPTY;
        int resultCount = hasResult ? buf.readVarInt() : 0;
        return new NetworkItemCountsPacket(counts, replace, hasNetwork, netId, resultItem, resultCount);
    }

    public static void handle(NetworkItemCountsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!ctx.get().getDirection().getReceptionSide().isClient()) return;
            if (!msg.hasNetwork) {
                NetworkItemCache.setAll(msg.counts, false, msg.netId);
                return;
            }
            NetworkItemCache.setAll(msg.counts, true, msg.netId);
            ToastDispatcher.show(msg.resultItem, msg.resultCount);
        });
        ctx.get().setPacketHandled(true);
    }
}
