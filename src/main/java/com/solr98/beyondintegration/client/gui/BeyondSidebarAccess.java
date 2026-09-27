package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import net.minecraft.client.gui.components.AbstractButton;

import java.util.List;

/**
 * BD 左侧按钮栏（LeftButtonSidebar）的接管访问接口。
 * 由 LeftButtonSidebarMixin 实现，暴露 addButton 拦截记录的全部按钮（按添加顺序）。
 */
public interface BeyondSidebarAccess {
    /** 侧栏中按添加顺序记录的全部按钮 */
    List<AbstractButton> beyond$trackedButtons();
}
