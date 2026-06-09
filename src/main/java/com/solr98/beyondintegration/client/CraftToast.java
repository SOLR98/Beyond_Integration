package com.solr98.beyondintegration.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class CraftToast implements Toast {

    private static final ResourceLocation TOAST_TEXTURE = new ResourceLocation("minecraft:textures/gui/toasts.png");

    private final ItemStack result;
    private final int count;
    private final String titleText;

    public CraftToast(ItemStack result, int count) {
        this.result = result;
        this.count = count;
        this.titleText = Component.translatable("message.beyond_integration.craft_toast_title").getString();
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toastComponent, long timer) {
        g.blit(TOAST_TEXTURE, 0, 0, 0, 0, this.width(), this.height());
        g.renderFakeItem(result, 8, 8);

        String desc = result.getHoverName().getString();
        if (count > 1) {
            desc += " \u00d7" + count;
        }

        g.drawString(toastComponent.getMinecraft().font, titleText, 30, 7, 0xFFFF5500);
        g.drawString(toastComponent.getMinecraft().font, desc, 30, 18, 0xFFFFFF);

        return timer >= 2500L ? Visibility.HIDE : Visibility.SHOW;
    }

    public static void show(ItemStack result, int count) {
        Minecraft.getInstance().getToasts().addToast(new CraftToast(result, count));
    }
}
