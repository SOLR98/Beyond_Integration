package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import net.minecraft.client.gui.components.AbstractButton;

import java.util.List;

/**
 * BD 左侧按钮栏接管访问接口。
 * 由 DimensionsNetGUIMixin 实现，暴露本模组接管的全部左侧按钮（BD 原生 + 本模组），
 * 供 LeftSidebarLayout 重排与 JEI 避让使用。不依赖 BD 的 LeftButtonSidebar，兼容 0.7.27 / 0.7.30。
 */
public interface BeyondSidebarAccess {
    /** 接管布局的左侧按钮（BD 原生在前、本模组在后） */
    List<AbstractButton> beyond$trackedButtons();
}
