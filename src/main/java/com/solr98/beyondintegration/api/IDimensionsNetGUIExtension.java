package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;

public interface IDimensionsNetGUIExtension {

    default void onInit(DimensionsNetGUI<?> gui) {}

    default void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {}

    /** @return true if the click was handled */
    default boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        return false;
    }

    /** Lower priority = processed first. Range 0-100 */
    default int priority() { return 50; }
}
