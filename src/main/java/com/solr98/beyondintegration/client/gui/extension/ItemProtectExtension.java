package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import com.solr98.beyondintegration.network.ProtectItemPacket;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ItemProtectExtension implements IDimensionsNetGUIExtension {

    @Override
    public int priority() { return 2; }

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

            PacketDistributor.sendToServer(new ProtectItemPacket(slotIndex));
            player.displayClientMessage(
                    Component.translatable(ItemTooltipHandler.isProtected(stack)
                            ? "message.beyond_integration.protect.removed"
                            : "message.beyond_integration.protect.added",
                            stack.getDisplayName()), true);
            return true;
        }
        return false;
    }
}
