package com.solr98.beyondintegration.client.gui.extension;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;

/**
 * BD 界面扩展辅助工具：读取 BD 菜单的 Shift 键状态（用于批量操作判定），
 * 并提供大数字缩写格式化（K/M/B）。
 */
public final class BDGUIHelper {

    /** 判断 BD 菜单当前是否按住 Shift（批量操作判定用，读取失败返回 false） */
    public static boolean isShiftDown(DimensionsNetGUI<?> gui) {
        try {
            var menu = gui.getMenu();
            if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown;
        } catch (Exception ignored) {}
        return false;
    }

    /** 大数缩写格式化：≥1000 用 K/M/B 单位（保留一位小数），小于 1000 原样输出 */
    public static String compactFormat(long value) {
        if (value >= 1_000_000_000L) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L)     return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000L)         return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
