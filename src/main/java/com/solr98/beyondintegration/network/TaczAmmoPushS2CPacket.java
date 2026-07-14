package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class TaczAmmoPushS2CPacket {

    private final Map<String, Integer> updates;

    public TaczAmmoPushS2CPacket(Map<String, Integer> updates) {
        this.updates = updates;
    }

    public static void encode(TaczAmmoPushS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.updates.size());
        for (var entry : msg.updates.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    public static TaczAmmoPushS2CPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Integer> updates = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            updates.put(buf.readUtf(), buf.readVarInt());
        }
        return new TaczAmmoPushS2CPacket(updates);
    }

    public static void handle(TaczAmmoPushS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                for (var entry : msg.updates.entrySet()) {
                    TaczAmmoCache.applyPush(entry.getKey(), entry.getValue());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
