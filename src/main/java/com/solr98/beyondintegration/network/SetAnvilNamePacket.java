package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.DimensionsAnvilMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetAnvilNamePacket(int containerId, String name) {

    public static void encode(SetAnvilNamePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeUtf(msg.name, 50);
    }

    public static SetAnvilNamePacket decode(FriendlyByteBuf buf) {
        return new SetAnvilNamePacket(buf.readVarInt(), buf.readUtf(50));
    }

    public static void handle(SetAnvilNamePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof DimensionsAnvilMenu menu && menu.containerId == msg.containerId) {
                menu.rename(msg.name);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
