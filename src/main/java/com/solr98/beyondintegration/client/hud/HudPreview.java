package com.solr98.beyondintegration.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD 元素在位置编辑界面中的预览/尺寸描述（客户端）。
 */
public interface HudPreview {

    /** 命中/拖动区域宽度。 */
    int width();

    /** 命中/拖动区域高度。 */
    int height();

    /** 在 (x,y)（左上角）绘制预览。 */
    void render(GuiGraphics g, int x, int y, float partialTicks);

    /**
     * 该 HUD 当前是否可用（决定是否出现在位置编辑器中）。
     * <p>依赖的模组/类缺失时应返回 {@code false}，避免编辑器加载其类导致
     * {@link NoClassDefFoundError}（例如未安装 Goety 时的网络灵魂条）。
     */
    default boolean isAvailable() {
        return true;
    }

    /** 默认 X（屏幕坐标），供编辑器在无布局时展示。 */
    default int defaultX(int screenWidth, int screenHeight) {
        return (screenWidth - width()) / 2;
    }

    /** 默认 Y（屏幕坐标），供编辑器在无布局时展示。 */
    default int defaultY(int screenWidth, int screenHeight) {
        return screenHeight / 2;
    }
}
