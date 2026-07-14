package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsGrindMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DimensionsGrindGUI extends DimensionsStorageGUI<DimensionsGrindMenu> {
    private static final ResourceLocation BG=ResourceLocation.tryParse("beyond_integration:textures/gui/grindstone.png");
    public DimensionsGrindGUI(DimensionsGrindMenu c, Inventory p, Component t) { super(c,p,t); }
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        g.blit(BG,this.leftPos,getGapY(),0,0,176,62,176,62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.grind"), this.leftPos + 6, getGapY() - 7, 0x404040, false);
    }
}
