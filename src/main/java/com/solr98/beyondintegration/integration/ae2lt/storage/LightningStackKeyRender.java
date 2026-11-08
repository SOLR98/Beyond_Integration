package com.solr98.beyondintegration.integration.ae2lt.storage;

import appeng.api.client.AEKeyRendering;
import com.moakiee.ae2lt.api.lightning.LightningTier;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.util.StringFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 闪电存储键的渲染：以水贴图染色表示高压（青）/ 超高压（紫）。
 */
public final class LightningStackKeyRender implements IStackRender {

    public static final LightningStackKeyRender INSTANCE = new LightningStackKeyRender();

    private LightningStackKeyRender() {
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics gui, IStackKey<?> key, int x, int y) {
        // 使用 AE2LT 同款闪电图标（其客户端已通过 AEKeyRendering.register 注册）。
        LightningTier tier = key instanceof LightningStackKey lightning
                ? lightning.tier()
                : LightningTier.HIGH_VOLTAGE;
        AEKeyRendering.drawInGui(Minecraft.getInstance(), gui, x, y, LightningKey.of(tier));
    }

    @Override
    public void renderAmount(GuiGraphics gui, long amount, int x, int y) {
        String text = getCountText(amount);
        if (text.isEmpty()) return;
        float scale = 0.666f;
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        pose.scale(scale, scale, scale);
        RenderSystem.disableBlend();
        int width = Minecraft.getInstance().font.width(text);
        int renderX = (int) ((x - 1 + 18.0f - width * scale) / scale);
        int renderY = (int) ((y - 1 + 16.0f - 5.0f * scale) / scale);
        gui.drawString(Minecraft.getInstance().font, text, renderX, renderY, 0xFFFFFF);
        pose.popPose();
    }

    @Override
    public String getCountText(long count) {
        return count < 0 ? "" : StringFormat.formatCompact(count);
    }

    @Override
    public Component getDisplayName(IStackKey<?> key) {
        return key instanceof LightningStackKey lightning
                ? lightning.getRenderStack().getName()
                : LightningStackKey.HIGH_VOLTAGE.getRenderStack().getName();
    }

    @Override
    public List<Component> getTooltipLines(IStackKey<?> key, long amount,
                                           @Nullable Player player,
                                           TooltipFlag tooltipFlag) {
        return List.of(getDisplayName(key),
                Component.translatable("istack.beyonddimensions.storage_num.long_type", amount));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(IStackKey<?> key) {
        return Optional.empty();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderTooltip(GuiGraphics gui, Font font, IStackKey<?> key, long amount, int mouseX, int mouseY) {
        var mc = Minecraft.getInstance();
        gui.renderTooltip(mc.font,
                getTooltipLines(key, amount, mc.player,
                        mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL),
                getTooltipImage(key), ItemStack.EMPTY, mouseX, mouseY);
    }
}
