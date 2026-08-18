package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 附魔书分离开关按钮（16×16）：切换附魔书/经验分离功能（SuperbAmmoCache）。
 * 按开关状态绘制对应纹理（正常/禁用/悬停）+ 附魔书图标，并提供动态 tooltip。
 */
public class EnchantToggleBtn extends Button {
    // 按钮三态纹理：正常 / 禁用 / 悬停
    private static final ResourceLocation SLOT = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_DISABLED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_disabled.png");
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    public EnchantToggleBtn(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        // 按钮纹理表示模式：开=正常，关=禁用（暗）纹理
        boolean on = SuperbAmmoCache.getEnchantSeparation();
        var tex = !on ? SLOT_DISABLED : (isHovered ? SLOT_HOVERED : SLOT);
        g.blit(tex, getX(), getY(), 0, 0, 16, 16, 16, 16);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(getX() + 1, getY() + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        g.renderFakeItem(new ItemStack(Items.ENCHANTED_BOOK), 0, 0);
        pose.popPose();
    }

    // 按当前开关状态刷新 tooltip 文本
    public void updateTooltip() {
        boolean on = SuperbAmmoCache.getEnchantSeparation();
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.beyond_integration.enchant_sep",
                        Component.translatable(on ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"))));
    }
}
