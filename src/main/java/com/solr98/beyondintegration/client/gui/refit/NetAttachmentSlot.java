package com.solr98.beyondintegration.client.gui.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.IStackTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * 网络配件槽位：视觉沿用 tacz 的改装槽位贴图，右下角叠加网络数量角标。
 * <p>与 {@code InventoryAttachmentSlot} 的区别：不绑定背包槽位号，只持有服务端下发的
 * 去重条目（attachmentId + 显示栈 + 总数量）。
 */
public class NetAttachmentSlot extends Button implements IStackTooltip {

    private final ItemStack display;
    private final int count;

    public NetAttachmentSlot(int x, int y, ItemStack display, int count, Button.OnPress onPress) {
        super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.display = display == null ? ItemStack.EMPTY : display;
        this.count = count;
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, width, height, 18, 18);
        } else {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, width - 2, height - 2, 18, 18);
        }
        if (!display.isEmpty()) {
            graphics.renderItem(display, x + 1, y + 1);
        }

        // 数量角标（总是显示，0.5 缩放，右下角）
        String text = count > 0 ? String.valueOf(count) : "";
        if (!text.isEmpty()) {
            var font = Minecraft.getInstance().font;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 250);
            pose.scale(0.5f, 0.5f, 1f);
            int textX = (x + 18) * 2 - font.width(text) - 2;
            int textY = (y + 18) * 2 - 10;
            graphics.drawString(font, text, textX, textY, 0xFFFFFFFF, true);
            pose.popPose();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (isHoveredOrFocused() && !display.isEmpty()) {
            consumer.accept(display);
        }
    }
}
