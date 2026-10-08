package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.FeederThirstHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 网络喂食器：直接整段替换 BD {@code NetFeederItem.workContent}，由 BI 完全接管
 * （喂食档位喂食物 + 补水档位补水，物品/流体/标记统一处理）。
 * 仅在 {@code thirst} 已加载时由 {@code MixinPlugin} 应用。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.item.NetFeederItem.class, remap = false)
public class FeederThirstItemMixin
{
    @Overwrite(remap = false)
    public void workContent(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected)
    {
        if (level.isClientSide()) return;
        FeederThirstHandler.handle(stack, level, holder);
    }
}
