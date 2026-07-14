package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DimensionsCraftGUI extends DimensionsStorageGUI<DimensionsCraftMenu> {
    private static final ResourceLocation BG = ResourceLocation.tryParse("beyond_integration:textures/gui/craft.png");
    public static final int PANEL_H = 72;

    public DimensionsCraftGUI(DimensionsCraftMenu c, Inventory p, Component t) { super(c, p, t); }

    @Override protected int getPanelHeight() { return PANEL_H; }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        int lx = this.leftPos;
        g.blit(BG, lx, gy, 0, 0, 176, PANEL_H, 176, PANEL_H);

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                lx + 50, gy + 67, 30,
                (float)(lx + 50) - mouseX, (float)(gy + 36) - mouseY, player);
        }

        g.drawString(mc.font, Component.translatable("gui.beyond_integration.workstation.craft"), lx + 6, gy - 7, 0x404040, false);
    }
}
