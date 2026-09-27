package com.solr98.beyondintegration.client.gui.extension;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


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

    /** 大数缩写格式化（统一委托 {@link com.solr98.beyondintegration.util.NumberFormatUtil#compact}） */
    public static String compactFormat(long value) {
        return com.solr98.beyondintegration.util.NumberFormatUtil.compact(value);
    }
}
