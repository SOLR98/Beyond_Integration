package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleEnchantSeparationPacket {

    public ToggleEnchantSeparationPacket() {}

    public static void encode(ToggleEnchantSeparationPacket msg, FriendlyByteBuf buf) {}

    public static ToggleEnchantSeparationPacket decode(FriendlyByteBuf buf) {
        return new ToggleEnchantSeparationPacket();
    }

    public static void handle(ToggleEnchantSeparationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.cannot_toggle_enchant_sep"));
                return;
            }
            if (net instanceof EnchantSeparationAccessor ea) {
                ea.beyond$setEnchantSeparationEnabled(!ea.beyond$isEnchantSeparationEnabled());
                net.setDirty();
                PacketHandler.sendToPlayer(player, new EnchantSeparationSyncPacket(ea.beyond$isEnchantSeparationEnabled()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
