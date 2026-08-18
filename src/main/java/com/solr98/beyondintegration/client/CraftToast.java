package com.solr98.beyondintegration.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 合成完成提示 Toast：显示合成结果物品图标、名称与数量，
 * 持续 2.5 秒后自动隐藏。
 */
public class CraftToast implements Toast {

    /** Toast 背景纹理（原版 toast 贴图） */
    private static final ResourceLocation TOAST_TEXTURE = new ResourceLocation("minecraft:textures/gui/toasts.png");

    /** 合成结果物品 */
    private final ItemStack result;
    /** 合成数量 */
    private final int count;
    /** 标题文本（本地化后的"合成完成"） */
    private final String titleText;

    /** 构造：固定标题文本，结果与数量用于渲染 */
    public CraftToast(ItemStack result, int count) {
        this.result = result;
        this.count = count;
        this.titleText = Component.translatable("message.beyond_integration.craft_toast_title").getString();
    }

    /** 渲染 Toast：背景、物品图标、标题与结果描述；超过 2500ms 返回 HIDE */
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

    /** 静态入口：结果非空且数量>0 时在 Toast 栏添加提示 */
    public static void show(ItemStack result, int count) {
        if (!result.isEmpty() && count > 0) {
            Minecraft.getInstance().getToasts().addToast(new CraftToast(result, count));
        }
    }
}
