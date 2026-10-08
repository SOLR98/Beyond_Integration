package com.solr98.beyondintegration.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** 快捷栏上方的“复制配置”提示叠加层（1.20.1）。 */
public class NetHintOverlay implements IGuiOverlay {

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick,
                       int screenWidth, int screenHeight) {
        NetHintHelper.render(guiGraphics, screenWidth, screenHeight);
    }
}
