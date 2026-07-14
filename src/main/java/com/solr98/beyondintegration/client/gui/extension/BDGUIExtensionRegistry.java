package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BD 网络主界面左侧工具栏扩展注册中心。
 *
 * <p>BD 原版左侧按钮（DimensionsNetGUI.init，x = leftPos - 18）：
 * <pre>
 *   行 0   sortButton           排序主策略
 *   行 1   secondSortButton     排序副策略
 *   行 2   reverseButton        升序/降序切换
 *   行 3   searchToggleButton   搜索框显隐
 *   行 4   addPageButton        增加行数
 *   行 5   removePageButton     减少行数
 *   行 6   craftButton          打开合成界面
 *   行 7   primaryNetSwitcher   主网络切换器（BD 原装）
 *   ─── BD_BUTTON_COUNT = 8 ───
 *   行 8   [EnchantSeparation]  附魔分离开关（slot 0）
 *   行 9   [StorageMenu]        存储界面（slot 1）
 * </pre>
 * 扩展通过 {@link #getSlotY(int, int)} 计算 Y 坐标，只需指定 slot 序号。
 * BD 按钮数量变化时仅需修改 {@link #BD_BUTTON_COUNT}。
 */
public final class BDGUIExtensionRegistry {

    /** BD 原版左侧按钮占据的行数（行 0~7） */
    public static final int BD_BUTTON_COUNT = 8;

    private static final List<IDimensionsNetGUIExtension> EXTENSIONS = new ArrayList<>();
    private static boolean registered;

    public static void ensureRegistered() {
        if (registered) return;
        registered = true;
        register(new EnchantSeparationExtension());
        register(new AmmoPanelExtension());
        register(new ItemProtectExtension());
        // 模式按钮已移至 DimensionsNetGUIMixin 右侧渲染（x=177），不再使用左侧工具栏扩展
    }

    public static void register(IDimensionsNetGUIExtension ext) {
        EXTENSIONS.add(ext);
        EXTENSIONS.sort(Comparator.comparingInt(IDimensionsNetGUIExtension::priority));
    }

    public static List<IDimensionsNetGUIExtension> getExtensions() {
        return EXTENSIONS;
    }

    /**
     * 计算第 slot 个扩展按钮的 Y 坐标。
     * <p>
     * 公式：Y = guiTop + 6 + 18 * (BD_BUTTON_COUNT + slot)
     * 第 0 个扩展紧接在最后一个 BD 按钮下方。
     *
     * @param guiTop  界面的 topPos
     * @param slot    扩展序号（0 = 第一个扩展, 1 = 第二个, ...）
     * @return 按钮的 Y 像素坐标
     */
    public static int getSlotY(int guiTop, int slot) {
        return guiTop + 6 + 18 * (BD_BUTTON_COUNT + slot);
    }
}
