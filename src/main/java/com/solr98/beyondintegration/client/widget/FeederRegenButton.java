package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.feature.feeder.FeederRegenMode;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 喂食器「回血模式」侧面开关按钮：图标取自原版 GUI 心形图标（满心 / 心形容器）。
 */
public class FeederRegenButton extends RightTabButton
{
    /** 原版 GUI 图标集（心形来源） */
    private static final ResourceLocation ICONS = ResourceLocation.tryBuild("minecraft", "textures/gui/icons.png");

    public FeederRegenButton(int x, int y, OnPress onPress)
    {
        super(x, y, 23, 26, x + 3, y + 4, 16, 16, onPress);
    }

    @Override
    protected void initButton()
    {
        states.add(FeederRegenMode.OFF);
        states.add(FeederRegenMode.ON);

        tooltipMap.put(FeederRegenMode.OFF, Tooltip.create(Component.translatable("tooltip.button.beyond_integration.feeder_regen_off")));
        tooltipMap.put(FeederRegenMode.ON, Tooltip.create(Component.translatable("tooltip.button.beyond_integration.feeder_regen_on")));

        setState(FeederRegenMode.OFF);
    }

    @Override
    protected void drawIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        // 9x9 原版心形图标居中于 16x16 图标区
        int x = getX() + 6;
        int y = getY() + 7;
        int u = currentState == FeederRegenMode.ON ? 52 : 16; // 满心 / 心形容器
        guiGraphics.blit(ICONS, x, y, u, 0, 9, 9, 256, 256);
    }
}
