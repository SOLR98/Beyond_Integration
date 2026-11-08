package com.solr98.beyondintegration.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物品附魔分离保护包（C2S）：客户端请求切换指定槽位物品的「附魔分离保护」标记，
 * 服务端在 NBT 上写/删标记并提示玩家。
 */
public record ProtectItemPacket(int slotIndex) implements CustomPacketPayload {

    public static final Type<ProtectItemPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "protect_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProtectItemPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ProtectItemPacket::slotIndex,
                    ProtectItemPacket::new);

    /** 服务端执行：校验槽位后切换物品的 beyond_integration:protect_sep NBT 标记 */
    public static void handle(final ProtectItemPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.slotIndex() >= player.getInventory().getContainerSize()) return;
            ItemStack stack;
            if (packet.slotIndex() >= 0) {
                stack = player.getInventory().getItem(packet.slotIndex());
            } else {
                stack = player.getMainHandItem();
            }
            if (stack.isEmpty()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.beyond_integration.protect.no_item"), true);
                return;
            }

            boolean isProtected = com.solr98.beyondintegration.core.util.ItemNbt
                    .read(stack).getBoolean("beyond_integration:protect_sep");
            if (isProtected) {
                com.solr98.beyondintegration.core.util.ItemNbt.update(stack,
                        tag -> tag.remove("beyond_integration:protect_sep"));
            } else {
                com.solr98.beyondintegration.core.util.ItemNbt.update(stack,
                        tag -> tag.putBoolean("beyond_integration:protect_sep", true));
            }
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(isProtected
                            ? "message.beyond_integration.protect.removed"
                            : "message.beyond_integration.protect.added", stack.getDisplayName()), true);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
