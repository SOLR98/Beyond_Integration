package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.magnet.MagnetHandler;
import com.wintercogs.beyonddimensions.common.item.NetMagnetItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 网络磁铁：接管 BD {@code NetMagnetItem.workContent} / {@code getTicksPerWork}，由 BI 接管，
 * 使“吸取范围 / 间隔”使用 BI 自定义档位列表（见 {@link MagnetHandler}）。
 * <p>均在 HEAD 注入并取消原逻辑（不整段替换方法体），保留原方法内的注入点，
 * 避免与 rs_integration 等对 workContent 内调用点的 @WrapOperation 注入冲突。
 */
@Mixin(value = NetMagnetItem.class, remap = false)
public class NetMagnetItemMixin {

    @Inject(method = "workContent", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$workContent(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected, CallbackInfo ci) {
        if (!level.isClientSide()) {
            MagnetHandler.handle(stack, level, holder, slotId, isSelected);
        }
        ci.cancel();
    }

    @Inject(method = "getTicksPerWork", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$ticksPerWork(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(MagnetHandler.ticksPerWork(stack));
    }
}
