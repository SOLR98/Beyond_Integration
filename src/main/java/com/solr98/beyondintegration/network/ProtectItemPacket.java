package com.solr98.beyondintegration.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ProtectItemPacket {

    private final int slotIndex;

    public ProtectItemPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public static void encode(ProtectItemPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slotIndex);
    }

    public static ProtectItemPacket decode(FriendlyByteBuf buf) {
        return new ProtectItemPacket(buf.readInt());
    }

    public static void handle(ProtectItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack stack;
            if (msg.slotIndex >= 0) {
                stack = player.getInventory().getItem(msg.slotIndex);
            } else {
                stack = player.getMainHandItem();
            }
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.beyond_integration.protect.no_item"), true);
                return;
            }

            boolean isProtected = stack.getOrCreateTag().getBoolean("beyond_integration:protect_sep");
            if (isProtected) {
                stack.getTag().remove("beyond_integration:protect_sep");
                if (stack.getTag().isEmpty()) stack.setTag(null);
            } else {
                stack.getOrCreateTag().putBoolean("beyond_integration:protect_sep", true);
            }
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(isProtected
                            ? "message.beyond_integration.protect.removed"
                            : "message.beyond_integration.protect.added", stack.getDisplayName()), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
