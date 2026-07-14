package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoTracker;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class RequestAmmoCountPacket {

    public RequestAmmoCountPacket() {}

    public static void encode(RequestAmmoCountPacket msg, FriendlyByteBuf buf) {}

    public static RequestAmmoCountPacket decode(FriendlyByteBuf buf) {
        return new RequestAmmoCountPacket();
    }

    public static void handle(RequestAmmoCountPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary == null) return;

            Map<String, Integer> ammoMap = TaczAmmoTracker.getOrCreate(primary).getAllCounts();
            PacketHandler.sendToPlayer(player, new AmmoCountResponsePacket(ammoMap));
        });
        ctx.get().setPacketHandled(true);
    }
}
