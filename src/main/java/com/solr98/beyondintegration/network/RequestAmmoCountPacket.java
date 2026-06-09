package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.NetworkAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RequestAmmoCountPacket {

    private final ResourceLocation ammoId;
    private final boolean quick;

    public RequestAmmoCountPacket(ResourceLocation ammoId, boolean quick) {
        this.ammoId = ammoId;
        this.quick = quick;
    }

    public static void encode(RequestAmmoCountPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.ammoId);
        buf.writeBoolean(msg.quick);
    }

    public static RequestAmmoCountPacket decode(FriendlyByteBuf buf) {
        return new RequestAmmoCountPacket(buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(RequestAmmoCountPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Map<Integer, Integer> networkCounts = new LinkedHashMap<>();

            if (msg.quick) {
                for (DimensionsNet net : DimensionsNet.getAllNetFromPlayer(player)) {
                    if (!networkCounts.containsKey(net.getId())) {
                        networkCounts.put(net.getId(),
                                NetworkAmmoExtractor.countAmmoInNetworkByAmmoId(msg.ammoId, net));
                    }
                }
                if (networkCounts.isEmpty()) {
                    DimensionsNet terminal = NetworkAmmoExtractor.findTerminalInInventory(player);
                    if (terminal != null) {
                        networkCounts.put(terminal.getId(),
                                NetworkAmmoExtractor.countAmmoInNetworkByAmmoId(msg.ammoId, terminal));
                    }
                }
            } else {
                DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (primary != null) {
                    networkCounts.put(primary.getId(),
                            NetworkAmmoExtractor.countAmmoInNetworkByAmmoId(msg.ammoId, primary));
                }

                for (DimensionsNet terminal : NetworkAmmoExtractor.findAllTerminalsInInventory(player)) {
                    if (!networkCounts.containsKey(terminal.getId())) {
                        networkCounts.put(terminal.getId(),
                                NetworkAmmoExtractor.countAmmoInNetworkByAmmoId(msg.ammoId, terminal));
                    }
                }
            }

            if (networkCounts.isEmpty()) {
                networkCounts.put(-1, 0);
            }

            PacketHandler.sendToPlayer(player, new AmmoCountResponsePacket(msg.ammoId, networkCounts, msg.quick));
        });
        ctx.get().setPacketHandled(true);
    }
}
