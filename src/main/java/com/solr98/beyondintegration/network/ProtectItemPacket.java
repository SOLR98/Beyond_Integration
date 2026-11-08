package com.solr98.beyondintegration.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 物品附魔分离保护包（C2S）：客户端请求切换指定槽位物品的"附魔分离保护"标记，服务端在 NBT 上写/删标记并提示玩家 */
public class ProtectItemPacket {

    /** 要操作的背包槽位索引；负数表示主手物品 */
    private final int slotIndex;

    public ProtectItemPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    /** 将槽位索引写入缓冲区 */
    public static void encode(ProtectItemPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slotIndex);
    }

    /** 从缓冲区读取槽位索引并还原数据包 */
    public static ProtectItemPacket decode(FriendlyByteBuf buf) {
        return new ProtectItemPacket(buf.readInt());
    }

    /** 服务端执行：校验槽位后切换物品的 beyond_integration:protect_sep NBT 标记，并向玩家发送结果提示 */
    public static void handle(ProtectItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.slotIndex >= player.getInventory().getContainerSize()) return;
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
