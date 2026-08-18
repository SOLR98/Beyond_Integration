package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 合成工作站界面：渲染工作台面板背景，并在面板中
 * 以跟随鼠标的方式实时渲染玩家实体（原版物品栏预览逻辑）。
 */
public class DimensionsCraftGUI extends DimensionsStorageGUI<DimensionsCraftMenu> {
    private static final ResourceLocation BG = ResourceLocation.tryParse("beyond_integration:textures/gui/craft.png");
    /** 工作站面板高度 */
    public static final int PANEL_H = 72;

    public DimensionsCraftGUI(DimensionsCraftMenu c, Inventory p, Component t) { super(c, p, t); }

    /** 返回面板高度（PANEL_H） */
    @Override protected int getPanelHeight() { return PANEL_H; }

    /** 渲染面板背景、跟随鼠标的玩家实体预览与标题 */
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        int lx = this.leftPos;
        g.blit(BG, lx, gy, 0, 0, 176, PANEL_H, 176, PANEL_H);

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null) {
            WorkstationRenderHelper.renderEntityInInventoryFollowsMouse(g,
                lx + 50, gy + 67, 30,
                (float)(lx + 50) - mouseX, (float)(gy + 36) - mouseY, player);
        }

        g.drawString(mc.font, Component.translatable("gui.beyond_integration.workstation.craft"), lx + 6, gy - 7, 0x404040, false);
    }
}
