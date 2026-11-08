package com.solr98.beyondintegration.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/** 快捷栏上方的“复制配置”提示叠加层（1.21.1）。 */
public class NetHintOverlay implements LayeredDraw.Layer {

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        NetHintHelper.render(guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }
}
