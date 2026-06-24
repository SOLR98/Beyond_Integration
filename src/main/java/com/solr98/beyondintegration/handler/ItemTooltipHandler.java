package com.solr98.beyondintegration.handler;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class ItemTooltipHandler {
    private static final String BCE_NET_ID_KEY = "Net_id";
    public static final String PROTECT_TAG = "beyond_integration:protect_sep";

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();

        // BD 网络终端/工具等物品
        Integer netId = stack.get(BDDataComponents.NET_ID_DATA.get());
        if (netId != null && netId >= 0) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.network_id", netId).withStyle(ChatFormatting.AQUA));
        }

        // SW container 物品（内含载具实体 NBT，可读绑定的网络 ID）
        if (stack.getItem() instanceof BlockItem) {
            var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (beData != null) {
                CompoundTag rootTag = beData.copyTag();
                if (rootTag.contains("Entity", CompoundTag.TAG_COMPOUND)) {
                    CompoundTag entityTag = rootTag.getCompound("Entity");
                    if (entityTag.contains(BCE_NET_ID_KEY)) {
                        netId = entityTag.getInt(BCE_NET_ID_KEY);
                        event.getToolTip().add(Component.translatable("tooltip.beyond_integration.network_id", netId).withStyle(ChatFormatting.AQUA));
                    }
                }
            }
        }

        // 附魔分离保护标记
        if (isProtected(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.beyond_integration.protected").withStyle(ChatFormatting.GREEN));
        }
    }

    public static boolean isProtected(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(PROTECT_TAG);
    }
}
