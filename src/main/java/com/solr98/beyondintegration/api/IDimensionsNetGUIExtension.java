package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 维度网络 GUI 扩展接口。
 * 用于在维度网络 GUI 初始化、渲染、鼠标点击等环节注入自定义行为，
 * 并通过 {@link #priority()} 控制多个扩展的执行优先级。
 */
public interface IDimensionsNetGUIExtension {

    /** GUI 初始化时回调（默认空实现）。 */
    default void onInit(DimensionsNetGUI<?> gui) {}

    /** GUI 渲染时回调（默认空实现）。 */
    default void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {}

    /**
     * 鼠标点击回调。
     * @return true 表示事件已被处理，不再传递给后续逻辑
     */
    default boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        return false;
    }

    /** 扩展执行优先级，数值越大越先执行，默认 50。 */
    default int priority() { return 50; }
}
