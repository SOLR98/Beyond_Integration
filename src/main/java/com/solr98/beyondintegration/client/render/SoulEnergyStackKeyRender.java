package com.solr98.beyondintegration.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solr98.beyondintegration.feature.soul.SoulEnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.util.IngredientRenderer;
import com.wintercogs.beyonddimensions.util.StringFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 网络灵魂能量键的客户端渲染器。
 * <p>图标：沿用水的静态贴图 + 灵魂青色 tint（避免依赖特定物品）；数量格式复用 BD {@link StringFormat}。
 * <p>仅在客户端调用（{@link IStackKey#getRender()}）。
 */
public class SoulEnergyStackKeyRender implements IStackRender {

    public static final SoulEnergyStackKeyRender INSTANCE = new SoulEnergyStackKeyRender();

    /** 灵魂青色 */
    private static final int SOUL_TINT = 0x8AD8FF;

    private SoulEnergyStackKeyRender() {
    }

    @Override
    public void render(GuiGraphics gui, IStackKey<?> key, int x, int y) {
        var pose = gui.pose();
        pose.pushPose();

        ResourceLocation still = IClientFluidTypeExtensions.of(Fluids.WATER).getStillTexture();
        TextureAtlasSprite sprite = still == null ? null
                : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);

        if (sprite != null && sprite.atlasLocation() != MissingTextureAtlasSprite.getLocation()) {
            IngredientRenderer.drawTiledSprite(gui, 16, 16, SOUL_TINT, 16, sprite, x, y);
        }

        pose.popPose();
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
        return StringFormat.formatCount(count);
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
