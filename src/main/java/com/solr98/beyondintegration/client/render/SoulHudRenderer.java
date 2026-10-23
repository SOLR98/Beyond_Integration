package com.solr98.beyondintegration.client.render;

import com.Polarice3.Goety.Goety;
import com.Polarice3.Goety.config.MainConfig;
import com.solr98.beyondintegration.util.NumberFormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * BI 网络灵魂条渲染器（客户端）。
 * <p>轨道/填充复用原版纹理；条头为维度网络发生器图集精灵；数值在条内居中。
 * 供 HUD（{@code SoulEnergyGuiMixin}）与位置编辑界面（{@code SoulHudPositionScreen}）共用。
 */
public final class SoulHudRenderer {

    /** 条区域宽/高（拖动命中与居中基准）。 */
    public static final int WIDTH = 128;
    public static final int HEIGHT = 9;
    /** 条头宽度。 */
    public static final int HEAD_W = 9;
    private static final int BAR_W = 117;
    private static final ResourceLocation NET_CREATER =
            ResourceLocation.tryBuild("beyonddimensions", "item/net_creater");
    /** Goety ≥ 2.5.59 自带灵魂增减弹字（{@code SoulChangePopup}）。 */
    private static final boolean GOETY_HAS_SOUL_POPUP = detectGoetyPopup();

    private SoulHudRenderer() {
    }

    /** 默认位置相对原版灵魂条的上移量：中间一行留给原版/网络灵魂增减弹字。 */
    public static int defaultRowOffset() {
        return 22;
    }

    /** Goety 是否自带灵魂增减弹字（≥ 2.5.59）。旧版由 BI 补显示。 */
    public static boolean goetyHasSoulPopup() {
        return GOETY_HAS_SOUL_POPUP;
    }

    private static boolean detectGoetyPopup() {
        try {
            Class.forName("com.Polarice3.Goety.client.gui.overlay.SoulChangePopup", false,
                    SoulHudRenderer.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 在 (x,y)（左上角）绘制网络灵魂条。 */
    public static void render(GuiGraphics g, int x, int y, long amount, long cap, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long denom = cap > 0L ? cap : Math.max(1, MainConfig.MaxArcaSouls.get());
        int offset = (int) ((mc.player.tickCount + partialTicks) % 234);

        // 轨道（复用原版灵魂条背景；头部会被下方发生器精灵覆盖）
        g.blit(Goety.location("textures/gui/soul_energy.png"), x, y, 0, 9, 128, 9, 128, 90);
        // 条头：维度网络发生器（图集精灵，动画/取帧由图集处理；缩放到 9×9）
        TextureAtlasSprite head = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(NET_CREATER);
        g.blit(x, y, 0, HEAD_W, HEIGHT, head);
        // 填充（复用原版能量条纹理，滚动动画）
        int len = (int) Math.min(BAR_W, Math.max(0L, Math.round(BAR_W * (amount / (double) denom))));
        g.blit(Goety.location("textures/gui/soul_energy_bar.png"), x + 9, y + 2, offset, 0, len, 5, 234, 5);

        if (MainConfig.ShowNum.get()) {
            String s = cap > 0L
                    ? amount + "/" + NumberFormatUtil.compactLarge(cap)
                    : Long.toString(amount);
            // 整条水平居中；不得覆盖条头（维度发生器），过宽时从发生器右缘向右扩展
            int textW = mc.font.width(s);
            int tx = x + (WIDTH - textW) / 2;
            int minX = x + HEAD_W;
            if (tx < minX) tx = minX;
            g.drawString(mc.font, s, tx, y + 1, 0xFFFFFF);
        }
    }
}
