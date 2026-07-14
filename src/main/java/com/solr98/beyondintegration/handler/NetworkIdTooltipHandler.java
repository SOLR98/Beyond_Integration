package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class NetworkIdTooltipHandler {

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
