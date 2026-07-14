package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EnchantSeparationSyncPacket {

    private final boolean enabled;

    public EnchantSeparationSyncPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(EnchantSeparationSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
    }

    public static EnchantSeparationSyncPacket decode(FriendlyByteBuf buf) {
        return new EnchantSeparationSyncPacket(buf.readBoolean());
    }

    public static void handle(EnchantSeparationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.setEnchantSeparation(msg.enabled);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
