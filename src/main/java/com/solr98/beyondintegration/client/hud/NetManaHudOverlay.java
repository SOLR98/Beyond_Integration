package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.client.ManaState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 方案 B：网络魔力独立 HUD（可拖动，走 {@link HudLayout} / {@link HudElement#NET_MANA}）。
 * <p>内容：缩小的维度发生器图标 + 网络魔力量；无网络源时不显示。
 */
public class NetManaHudOverlay implements IGuiOverlay {

    public static final NetManaHudOverlay INSTANCE = new NetManaHudOverlay();

    /** 尺寸（复用铁魔法法条 98×21）。 */
    public static final int WIDTH = NetManaHudRenderer.BAR_WIDTH;
    public static final int HEIGHT = NetManaHudRenderer.BAR_HEIGHT;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!com.solr98.beyondintegration.ClientConfig.ironsManaHud()) return;
        // 依赖 Iron's Spells 的纹理与状态；未安装时不绘制（防止缺图/异常）
        if (!net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) return;
        if (!ManaState.hasSource()) return;
        int x = HudLayout.resolveX(HudElement.NET_MANA, defaultX(screenWidth), screenWidth);
        int y = HudLayout.resolveY(HudElement.NET_MANA, defaultY(screenHeight), screenHeight);
        NetManaHudRenderer.renderBar(g, x, y);
    }

    /** 默认位置：快捷栏（原版 HUD）上方、水平居中。 */
    public static int defaultX(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    public static int defaultY(int screenHeight) {
        return screenHeight - 45;
    }
}
