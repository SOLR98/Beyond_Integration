package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.cache.AmmoCountCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class AmmoCountResponsePacket {

    private final ResourceLocation ammoId;
    private final Map<Integer, Integer> networkCounts;
    private final boolean quick;

    public AmmoCountResponsePacket(ResourceLocation ammoId, Map<Integer, Integer> networkCounts, boolean quick) {
        this.ammoId = ammoId;
        this.networkCounts = networkCounts;
        this.quick = quick;
    }

    public static void encode(AmmoCountResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.ammoId);
        buf.writeBoolean(msg.quick);
        buf.writeVarInt(msg.networkCounts.size());
        for (var entry : msg.networkCounts.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    public static AmmoCountResponsePacket decode(FriendlyByteBuf buf) {
        ResourceLocation ammoId = buf.readResourceLocation();
        boolean quick = buf.readBoolean();
        int size = buf.readVarInt();
        Map<Integer, Integer> networkCounts = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            int netId = buf.readVarInt();
            int count = buf.readVarInt();
            networkCounts.put(netId, count);
        }
        return new AmmoCountResponsePacket(ammoId, networkCounts, quick);
    }

    public static void handle(AmmoCountResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                AmmoCountCache.update(msg.ammoId, msg.networkCounts, msg.quick);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
