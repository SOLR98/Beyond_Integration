package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 费用悬浮框通用渲染工具：在给定文本区域内悬停时显示费用公式 / 配置 / 明细 tooltip。
 * 供附魔合并、铁砧等工作站界面复用。
 */
public final class CostTooltipHelper {
    private CostTooltipHelper() {}

    /**
     * 若鼠标位于 [x, x+w) × [y, y+h) 内，则渲染给定文本行组成的悬浮框。
     *
     * @param g     绘制上下文
     * @param font  字体（宽度/渲染）
     * @param mx    鼠标 x
     * @param my    鼠标 y
     * @param x     文本区域左（屏幕绝对坐标）
     * @param y     文本区域上（屏幕绝对坐标）
     * @param w     文本区域宽
     * @param h     文本区域高
     * @param lines 悬浮框文本行；为空则不显示
     */
    public static void render(GuiGraphics g, Font font, int mx, int my,
                              int x, int y, int w, int h, List<Component> lines) {
        if (lines == null || lines.isEmpty() || w <= 0) return;
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }
}
