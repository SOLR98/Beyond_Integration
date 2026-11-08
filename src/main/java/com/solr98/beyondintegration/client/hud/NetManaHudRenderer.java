package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.client.ManaState;
import com.solr98.beyondintegration.util.NumberFormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Locale;

/**
 * 网络魔力 HUD 绘制（客户端）。
 * <ul>
 *   <li>{@link #renderIcon}：缩小的“维度网络发生器”图标（BD {@code net_creater}，图集精灵，与灵魂条一致）；</li>
 *   <li>{@link #renderBar}：独立网络蓝条——<b>复用铁魔法法力条纹理</b>（背景 + 填充），
 *       数值文本<b>复用灵魂条样式</b>（占比居中、数字缩写、避开图标）。</li>
 * </ul>
 */
public final class NetManaHudRenderer {

    /** 维度网络发生器（图集精灵资源路径，与灵魂条一致）。 */
    public static final ResourceLocation NET_CREATER =
            ResourceLocation.tryBuild("beyonddimensions", "item/net_creater");

    /** 铁魔法法力条纹理（整图，与 {@code ManaBarOverlay.TEXTURE} 相同）。 */
    public static final ResourceLocation IRONS_ICONS =
            ResourceLocation.tryBuild("irons_spellbooks", "textures/gui/icons.png");

    /** 图标绘制尺寸（缩小，源 16×16）。 */
    public static final int ICON = 10;

    /** 独立条尺寸（复用铁魔法法条 98×21；背景 sprite 0,0 / 填充 sprite 0,21）。 */
    public static final int BAR_WIDTH = 98;
    public static final int BAR_HEIGHT = 21;

    /** 文本颜色（铁魔法法条 AQUA 系）。 */
    private static final int BAR_TEXT_COLOR = 0xFF55FFFF;

    private NetManaHudRenderer() {
    }

    /** 仅绘制缩小的维度发生器图标（左上角 x,y）——图集精灵，缩放至 ICON×ICON。 */
    public static void renderIcon(GuiGraphics g, int x, int y) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(NET_CREATER);
        g.blit(x, y, 0, ICON, ICON, sprite);
    }

    /**
     * 独立网络蓝条：复用铁魔法法力条纹理绘制“背景 + 填充”，中上部叠加缩小的维度发生器，
     * 数值文本复用灵魂条样式（占用比例居中、数字缩写、避开图标）。
     */
    public static void renderBar(GuiGraphics g, int x, int y) {
        long amount = Math.max(0L, ManaState.getAmount());
        long cap = ManaState.getCap();
        double ratio = cap > 0L ? Math.min(1.0, amount / (double) cap) : 0.0;

        // 背景（铁魔法法条 sprite 0,0）
        g.blit(IRONS_ICONS, x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, 256, 256);
        // 填充（sprite 0,21）
        int fill = (int) (BAR_WIDTH * ratio);
        if (fill > 0) {
            g.blit(IRONS_ICONS, x, y, 0, BAR_HEIGHT, fill, BAR_HEIGHT, 256, 256);
        }
        // 中上部：缩小的维度发生器（覆盖铁魔法法条中轴图标）
        renderIcon(g, x + BAR_WIDTH / 2 - ICON / 2, y - 2);

        // 数值文本（整条水平居中；当前值用本类缩写，上限仍用 SI 单位转换）
        var font = Minecraft.getInstance().font;
        String s = cap > 0L
                ? format(amount) + "/" + NumberFormatUtil.compactLarge(cap)
                : format(amount);
        int textW = font.width(s);
        int tx = x + (BAR_WIDTH - textW) / 2;
        int ty = y + (BAR_HEIGHT - font.lineHeight) / 2 + 1;
        g.drawString(font, s, tx, ty, BAR_TEXT_COLOR);
    }

    /**
     * 网络魔力“当前值”显示：达到 10× 单位才转换——{@code ≥10k} 用 {@code K}、{@code ≥10m} 用 {@code M}、
     * {@code ≥10b} 用 {@code B}、{@code ≥10t} 用 {@code T}；{@code <10k} 显示原值。
     * 单位<b>大写</b>，并<b>保留最多两位小数</b>（去尾零）。
     */
    public static String format(long v) {
        if (v < 0L) v = 0L;
        if (v < 10_000L) return Long.toString(v); // 未达 10k：原值
        long[] steps = {1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L};
        String[] units = {"K", "M", "B", "T"};
        int idx = -1;
        for (int i = 0; i < steps.length; i++) {
            if (v >= 10L * steps[i]) idx = i; // 达到 10× 该单位才采用
        }
        return unit(v / (double) steps[idx], units[idx]);
    }

    /** 数值 + 大写单位：整数不带小数，否则保留最多两位小数并去尾零。 */
    private static String unit(double value, String suffix) {
        if (value == Math.floor(value)) {
            return (long) value + suffix;
        }
        String text = String.format(Locale.US, "%.2f", value);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;
        return text.substring(0, end) + suffix;
    }
}
