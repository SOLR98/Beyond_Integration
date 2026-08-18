package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端物品 Tooltip 处理器。
 * 为带有 BD 网络 ID 的物品（兼容 TaCZ/NetedItem 及自定义 NBT 两种来源）
 * 添加"网络 ID"灰色提示行，并为带保护标记的物品添加绿色保护提示。
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class NetworkIdTooltipHandler {

    /** 物品 Tooltip 事件：追加网络 ID 与保护状态提示。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int netId = getNetId(stack);
        if (netId >= 0) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.net_id", netId)
                    .withStyle(ChatFormatting.GRAY));
        }

        if (stack.hasTag() && stack.getTag().getBoolean("beyond_integration:protect_sep")) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.protected")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** 尝试从物品栈中解析网络 ID：优先用 NetedItem，缺失时回退读取 NBT 的 "NetId"，失败返回 -1。 */
    private static int getNetId(ItemStack stack) {
        try {
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) return netId;
        } catch (NoClassDefFoundError ignored) {}
        if (stack.hasTag() && stack.getTag().contains("NetId")) {
            return stack.getTag().getInt("NetId");
        }
        return -1;
    }
}
