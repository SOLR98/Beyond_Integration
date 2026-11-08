package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import net.minecraft.world.item.ItemStack;

/**
 * 便携网络终端判定：BI 统一以「便携网络终端」（{@link NetTerminalItem}）作为代表维度网络的媒介。
 * <p>用于绑定来源与网络中继识别（凭证 / 女仆 / 实体 / 背包 / 牢笼魂符 / 假玩家匹配）。
 */
public final class NetTerminalHelper {

    private NetTerminalHelper() {}

    /** 物品是否为便携网络终端。 */
    public static boolean isTerminal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof NetTerminalItem;
    }
}
