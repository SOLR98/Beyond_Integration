package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestEnchantSeparationPacket {

    public RequestEnchantSeparationPacket() {}

    public static void encode(RequestEnchantSeparationPacket msg, FriendlyByteBuf buf) {}

    public static RequestEnchantSeparationPacket decode(FriendlyByteBuf buf) {
        return new RequestEnchantSeparationPacket();
    }

    public static void handle(RequestEnchantSeparationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            boolean enabled = !(net instanceof EnchantSeparationAccessor ea)
                    || ea.beyond$isEnchantSeparationEnabled();
            PacketHandler.sendToPlayer(player, new EnchantSeparationSyncPacket(enabled));
        });
        ctx.get().setPacketHandled(true);
    }
}
