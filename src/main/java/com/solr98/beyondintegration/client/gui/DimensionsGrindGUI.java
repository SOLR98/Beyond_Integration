package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsGrindMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 磨石工作站界面：渲染磨石面板背景，
 * 输入槽有物品但无结果时显示错误红叉提示。
 */
public class DimensionsGrindGUI extends DimensionsStorageGUI<DimensionsGrindMenu> {
    /** 磨石面板背景纹理 */
    private static final ResourceLocation BG = ResourceLocation.tryParse("beyond_integration:textures/gui/grindstone.png");
    // 原版 GrindstoneScreen：输入有物但结果为空时显示红叉（28x21）
    /** 无结果错误红叉图标纹理 */
    private static final ResourceLocation ERROR_ICON = ResourceLocation.tryParse("beyond_integration:textures/gui/notfor.png");
    public DimensionsGrindGUI(DimensionsGrindMenu c, Inventory p, Component t) { super(c, p, t); }
    /** 渲染磨石面板背景与"输入有物但无结果"错误红叉 */
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        g.blit(BG, this.leftPos, gy, 0, 0, 176, 62, 176, 62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.grind"), this.leftPos + 6, gy - 7, 0x404040, false);
        // 原版行为：slot0/1 任一有物且结果槽空 → 错误图标（原版 (92,31)，按 BI 槽位 y 偏移换算 31-19+6=18）
        if ((!this.menu.getInput().isEmpty() || !this.menu.getAdditional().isEmpty()) && this.menu.getOutput().isEmpty()) {
            g.blit(ERROR_ICON, this.leftPos + 92, gy + 18, 0, 0, 28, 21, 28, 21);
        }
    }
}
