package com.solr98.beyondintegration.client.gui.extension;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;

/**
 * BD 界面辅助工具（扩展专用）。
 * 提供 Shift 状态读取与长数字紧凑格式化（K/M/B）两个静态方法。
 */
public final class BDGUIHelper {
    /** 读取 BD 菜单的 Shift 按下状态（反射访问菜单字段，失败兜底 false） */
    public static boolean isShiftDown(DimensionsNetGUI<?> gui) {
        try { var menu = gui.getMenu(); if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown; } catch (Exception ignored) {}
        return false;
    }

    /** 长数字紧凑格式化（统一委托 {@link com.solr98.beyondintegration.util.NumberFormatUtil#compact}） */
    public static String compactFormat(long value) {
        return com.solr98.beyondintegration.util.NumberFormatUtil.compact(value);
    }
}

