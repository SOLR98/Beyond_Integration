package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.FeederThirstHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 网络喂食器：接管 BD {@code NetFeederItem.workContent}，由 BI 完全接管
 * （喂食档位喂食物 + 补水档位补水，物品/流体/标记统一处理）。
 * <p>HEAD 注入并取消原逻辑（不整段替换方法体），保留原方法内的注入点。
 * 仅在 {@code thirst} 已加载时由 {@code MixinPlugin} 应用。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.item.NetFeederItem.class, remap = false)
public class FeederThirstItemMixin
{
    @Inject(method = "workContent", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$workContent(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected, CallbackInfo ci)
    {
        if (!level.isClientSide()) {
            FeederThirstHandler.handle(stack, level, holder);
        }
        ci.cancel();
    }
}
