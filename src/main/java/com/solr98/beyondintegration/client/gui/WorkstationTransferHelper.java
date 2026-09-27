package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.util.UIDataHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;

/**
 * 工作站切换上下文保存工具：在 BD 网络界面 ↔ 工作站界面之间切换时，
 * 借助 BD 的 UIDataHelper 保存翻页位置与鼠标物理坐标，便于返回时恢复。
 */
// 统一使用 BD 的 UIDataHelper 保存界面切换上下文（翻页位置 + 鼠标物理坐标）
public final class WorkstationTransferHelper {
    /** 保存当前 BD 网络菜单的翻页数据与鼠标物理坐标，并标记为界面切换中 */
    public static void save(DimensionsNetMenu menu) {
        UIDataHelper.currentPage = menu.lineData;
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(Minecraft.getInstance().getWindow().getWindow(), x, y);
        UIDataHelper.lastMousePos = new Vec2((float) x[0], (float) y[0]);
        UIDataHelper.isTransfer = true;
    }

    /** 清除界面切换标记（界面关闭时调用） */
    public static void clearPending() {
        UIDataHelper.isTransfer = false;
    }

    private WorkstationTransferHelper() {}
}
