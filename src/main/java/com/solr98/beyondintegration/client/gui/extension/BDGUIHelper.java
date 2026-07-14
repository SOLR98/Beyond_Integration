package com.solr98.beyondintegration.client.gui.extension;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;

public final class BDGUIHelper {

    public static boolean isShiftDown(DimensionsNetGUI<?> gui) {
        try {
            var menu = gui.getMenu();
            if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown;
        } catch (Exception ignored) {}
        return false;
    }

    public static String compactFormat(long value) {
        if (value >= 1_000_000_000L) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L)     return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000L)         return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
