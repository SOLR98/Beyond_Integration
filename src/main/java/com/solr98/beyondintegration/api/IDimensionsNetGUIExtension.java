package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;

public interface IDimensionsNetGUIExtension {

    default void onInit(DimensionsNetGUI<?> gui) {}

    default void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {}

    default boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        return false;
    }

    default int priority() { return 50; }
}
