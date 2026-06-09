package com.solr98.beyondintegration.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class CraftToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_TIME_MS = 2500L;
    private final ItemStack result;
    private final int count;

    public CraftToast(ItemStack result, int count) {
        this.result = result;
        this.count = count;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent comp, long timer) {
        g.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        g.renderFakeItem(result, 8, 8);
        g.drawString(comp.getMinecraft().font, Component.translatable("toast.beyond_integration.craft_success"), 30, 7, 0xFFFF5500);
        Component desc = result.getHoverName();
        if (count > 1) desc = Component.literal("").append(desc).append(Component.literal(" \u00D7" + count));
        g.drawString(comp.getMinecraft().font, desc, 30, 18, 0xFFFFFF);
        return timer >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    public static void show(ItemStack result, int count) {
        Minecraft.getInstance().getToasts().addToast(new CraftToast(result, count));
    }
}
