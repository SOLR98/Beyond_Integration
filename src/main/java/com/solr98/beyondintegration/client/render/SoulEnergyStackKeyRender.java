package com.solr98.beyondintegration.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solr98.beyondintegration.feature.soul.SoulEnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.util.StringFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 网络灵魂能量键的客户端渲染器。
 * <p>图标：原版 soul 粒子贴图逐帧播放（{@code minecraft:textures/particle/soul_0..10.png}，共 11 帧，每帧 16×16）；数量格式复用 BD {@link StringFormat}。
 * <p>仅在客户端调用（{@link IStackKey#getRender()}）。
 */
public class SoulEnergyStackKeyRender implements IStackRender {

    public static final SoulEnergyStackKeyRender INSTANCE = new SoulEnergyStackKeyRender();

    /** 原版 soul 粒子贴图（soul_0 ~ soul_10 共 11 帧，每帧 16×16；soul_11 未被 vanilla 使用） */
    private static final ResourceLocation[] SOUL_FRAMES = new ResourceLocation[11];
    static {
        for (int i = 0; i < SOUL_FRAMES.length; i++) {
            SOUL_FRAMES[i] = ResourceLocation.tryParse("minecraft:textures/particle/soul_" + i + ".png");
        }
    }
    /** 每帧持续毫秒（≈12.5 fps，整循环约 1s） */
    private static final long FRAME_MS = 80L;

    private SoulEnergyStackKeyRender() {
    }

    @Override
    public void render(GuiGraphics gui, IStackKey<?> key, int x, int y) {
        int frame = (int) ((net.minecraft.Util.getMillis() / FRAME_MS) % SOUL_FRAMES.length);
        gui.blit(SOUL_FRAMES[frame], x, y, 0, 0, 16, 16, 16, 16);
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

        int w = Minecraft.getInstance().font.width(text);
        final int X = (int) ((x - 1 + 16.0f + 2.0f - w * 0.666f) / 0.666f);
        final int Y = (int) ((y - 1 + 16.0f - 5.0f * 0.666f) / 0.666f);
        gui.drawString(Minecraft.getInstance().font, text, X, Y, 0xFFFFFF);

        pose.popPose();
    }

    @Override
    public String getCountText(long count) {
        if (count < 0) return "";
        return StringFormat.formatCompact(count);
    }

    @Override
    public Component getDisplayName(IStackKey<?> key) {
        return SoulEnergyStackKey.INSTANCE.getRenderStack().getName();
    }

    @Override
    public List<Component> getTooltipLines(IStackKey<?> key, long amount,
                                           @Nullable Player player,
                                           TooltipFlag tooltipFlag) {
        return List.of(
                getDisplayName(key),
                Component.translatable("istack.beyonddimensions.storage_num.long_type", amount)
        );
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(IStackKey<?> key) {
        return Optional.empty();
    }

    @Override
    public void renderTooltip(GuiGraphics gui, Font font, IStackKey<?> key, long amount, int mouseX, int mouseY) {
        var mc = Minecraft.getInstance();
        gui.renderTooltip(
                mc.font,
                getTooltipLines(key, amount, mc.player,
                        mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL),
                getTooltipImage(key),
                net.minecraft.world.item.ItemStack.EMPTY,
                mouseX, mouseY
        );
    }
}
