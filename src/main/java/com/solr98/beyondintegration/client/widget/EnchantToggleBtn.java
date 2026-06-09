package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EnchantToggleBtn extends Button {
    private static final ResourceLocation SLOT = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.parse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    public EnchantToggleBtn(int x, int y, OnPress onPress) {
        super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        var tex = isHovered ? SLOT_HOVERED : SLOT;
        g.blit(tex, getX(), getY(), 0, 0, 16, 16, 16, 16);

        boolean on = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(getX() + 1, getY() + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        g.renderFakeItem(new ItemStack(Items.ENCHANTED_BOOK), 0, 0);
        if (!on) g.fill(0, 0, 18, 18, 0xC0646464);
        pose.popPose();
    }

    public void updateTooltip() {
        boolean on = SuperbAmmoCache.INSTANCE.getEnchantSeparation();
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.beyond_integration.enchant_sep",
                        Component.translatable(on ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"))));
    }
}
