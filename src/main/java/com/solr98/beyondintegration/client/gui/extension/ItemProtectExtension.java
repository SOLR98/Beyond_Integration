package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.ProtectItemPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 物品保护扩展：Ctrl + 右键 玩家背包槽位时，
 * 切换该物品的附魔分离保护标记（NBT）并同步服务端，并显示提示消息。
 */
public class ItemProtectExtension implements IDimensionsNetGUIExtension {

    @Override
    public int priority() { return 2; }

    /** Ctrl+右键玩家背包物品：切换保护标记并发送数据包（返回 true 表示已处理） */
    @Override
    public boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        if (button != 1 || !Screen.hasControlDown()) return false;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return false;

        for (var slot : gui.getMenu().slots) {
            if (slot.container != player.getInventory()) continue;
            int sx = gui.getGuiLeft() + slot.x;
            int sy = gui.getGuiTop() + slot.y;
            if (mx < sx || mx >= sx + 18 || my < sy || my >= sy + 18) continue;

            int slotIndex = slot.getSlotIndex();
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (stack.isEmpty()) return false;

            boolean isProtected = stack.getOrCreateTag().getBoolean("beyond_integration:protect_sep");
            if (isProtected) {
                stack.getTag().remove("beyond_integration:protect_sep");
                if (stack.getTag().isEmpty()) stack.setTag(null);
            } else {
                stack.getOrCreateTag().putBoolean("beyond_integration:protect_sep", true);
            }

            PacketHandler.sendToServer(new ProtectItemPacket(slotIndex));
            player.displayClientMessage(
                    Component.translatable(isProtected
                            ? "message.beyond_integration.protect.removed"
                            : "message.beyond_integration.protect.added",
                            stack.getDisplayName()), true);
            return true;
        }
        return false;
    }
}
