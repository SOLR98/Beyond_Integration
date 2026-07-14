package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class AmmoCountResponsePacket {

    private final Map<String, Integer> ammoMap;

    public AmmoCountResponsePacket(Map<String, Integer> ammoMap) {
        this.ammoMap = ammoMap;
    }

    public static void encode(AmmoCountResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ammoMap.size());
        for (var entry : msg.ammoMap.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    public static AmmoCountResponsePacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Integer> ammoMap = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ammoMap.put(buf.readUtf(), buf.readVarInt());
        }
        return new AmmoCountResponsePacket(ammoMap);
    }

    public static void handle(AmmoCountResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                TaczAmmoCache.update(msg.ammoMap);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
