package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.feature.feeder.FeederRegenMode;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 喂食器「回血模式」侧面开关按钮：图标取自原版 HUD 心形 sprite（满心 / 心形容器）。
 */
public class FeederRegenButton extends RightTabButton
{
    private static final ResourceLocation HEART_FULL = ResourceLocation.tryBuild("minecraft", "hud/heart/full");
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.tryBuild("minecraft", "hud/heart/container");

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
        int x = getX() + 6;
        int y = getY() + 7;
        ResourceLocation heart = currentState == FeederRegenMode.ON ? HEART_FULL : HEART_CONTAINER;
        guiGraphics.blitSprite(heart, x, y, 9, 9);
    }
}
