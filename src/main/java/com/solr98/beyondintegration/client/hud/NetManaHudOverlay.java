package com.solr98.beyondintegration.client.hud;

import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.client.ManaState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.fml.ModList;

/**
 * 网络魔力独立 HUD（铁魔法联动；走统一 HUD 布局，可拖动）。
 */
public class NetManaHudOverlay implements LayeredDraw.Layer {

    public static final NetManaHudOverlay INSTANCE = new NetManaHudOverlay();

    public static final int WIDTH = NetManaHudRenderer.BAR_WIDTH;
    public static final int HEIGHT = NetManaHudRenderer.BAR_HEIGHT;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        if (!ClientConfig.ironsManaHud()) return;
        if (!ModList.get().isLoaded("irons_spellbooks")) return;
        if (!ManaState.hasSource()) return;
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        int x = HudLayout.resolveX(HudElement.NET_MANA, defaultX(screenWidth), screenWidth);
        int y = HudLayout.resolveY(HudElement.NET_MANA, defaultY(screenHeight), screenHeight);
        NetManaHudRenderer.renderBar(g, x, y);
    }

    public static int defaultX(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    public static int defaultY(int screenHeight) {
        return screenHeight - 45;
    }
}
