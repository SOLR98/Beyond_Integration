package com.solr98.beyondintegration.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solr98.beyondintegration.feature.mana.ManaStackKey;
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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.ClientTooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 网络魔力键的客户端渲染器（NeoForge 1.21.1）。
 * <p>图标：水的静态贴图 + 魔力蓝 tint；数量格式复用 BD {@link StringFormat}。
 */
public class ManaStackKeyRender implements IStackRender {

    public static final ManaStackKeyRender INSTANCE = new ManaStackKeyRender();

    /** 魔力蓝 */
    private static final int MANA_TINT = 0x5DA8FF;

    private ManaStackKeyRender() {
    }

    @Override
    public void render(GuiGraphics gui, IStackKey<?> key, int x, int y) {
        var pose = gui.pose();
        pose.pushPose();
        ResourceLocation still = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
                .of(net.minecraft.world.level.material.Fluids.WATER).getStillTexture();
        TextureAtlasSprite sprite = still == null ? null
                : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
        if (sprite != null && sprite.atlasLocation() != MissingTextureAtlasSprite.getLocation()) {
            IngredientRenderer.drawTiledSprite(gui, 16, 16, MANA_TINT, 16, sprite, x, y);
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
        return ManaStackKey.INSTANCE.getRenderStack().getName();
    }

    @Override
    public List<Component> getTooltipLines(IStackKey<?> key, long amount, Item.TooltipContext tooltipContext,
                                           @Nullable net.minecraft.world.entity.player.Player player,
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
        var ctx = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
        gui.renderTooltip(
                mc.font,
                getTooltipLines(key, amount, ctx, mc.player,
                        ClientTooltipFlag.of(mc.options.advancedItemTooltips
                                ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL)),
                getTooltipImage(key),
                ItemStack.EMPTY,
                mouseX, mouseY
        );
    }
}
