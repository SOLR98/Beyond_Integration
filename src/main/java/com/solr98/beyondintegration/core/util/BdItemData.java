package com.solr98.beyondintegration.core.util;

import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * BD 数据组件访问兼容层。
 *
 * <p>BD 0.8.9 的 {@code 1.21.1} 分支把网络绑定 ID 与机器过滤槽从「便捷方法」重构成
 * 数据组件（{@code BDDataComponents.NET_ID_DATA} / {@code BDDataComponents.ISTACK_SLOTS}），
 * 而 BI 代码（对齐 {@code BeyondDimensions-1.20.1} 分支）用的是
 * {@code com.solr98.beyondintegration.core.util.BdItemData.netId(stack)} / {@code com.solr98.beyondintegration.core.util.BdItemData.filterSlots(stack, def)}。
 *
 * <p>本类把两者对上，BI 其余代码照常调用即可。
 */
public final class BdItemData {

    private BdItemData() {}

    /** 读取物品绑定的网络 ID；未绑定返回 −1。等价于 1.20.1 分支的 {@code NetedItem.getNetId}。 */
    public static int netId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        return stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
    }

    /** 写入物品绑定的网络 ID（{@code id < 0} 表示解绑）。 */
    public static void setNetId(ItemStack stack, int id) {
        if (stack == null || stack.isEmpty()) return;
        stack.set(BDDataComponents.NET_ID_DATA, id);
    }

    /** 读取机器过滤槽；无数据时返回 {@code defaultValue}。等价于 {@code getFilterSlotsOrDefault}。 */
    public static List<KeyAmount> filterSlots(ItemStack stack, List<KeyAmount> defaultValue) {
        if (stack == null || stack.isEmpty()) {
            return defaultValue == null ? new ArrayList<>() : defaultValue;
        }
        List<KeyAmount> stored = stack.get(BDDataComponents.ISTACK_SLOTS);
        if (stored == null || stored.isEmpty()) {
            return defaultValue == null ? new ArrayList<>() : defaultValue;
        }
        return stored;
    }

    /** 写回机器过滤槽。 */
    public static void setFilterSlots(ItemStack stack, List<KeyAmount> slots) {
        if (stack == null || stack.isEmpty()) return;
        stack.set(BDDataComponents.ISTACK_SLOTS,
                slots == null ? new ArrayList<>() : new ArrayList<>(slots));
    }
}
