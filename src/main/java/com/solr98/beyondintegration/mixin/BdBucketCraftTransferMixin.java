package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BD 合成菜单（含终端合成）网络提取增强：BD 原版 {@code extractFromStorage} 只按"桶物品键"
 * 从网络提取，网络只有流体（+空桶）时提取为 0、对应合成槽填不上；
 * 这里在返回值上做同样的"流体回退"（宽松语义：流体足够即视为提取到，网络有空容器时一并扣除），
 * 使原版 {@code transferRecipe} 的 got 计算照常把桶物品填入合成格。
 * <p>
 * 覆盖 BD 工作站合成、BD 终端合成与物质压缩球打包路径
 * （{@code transferRecipe} 与 {@code transferRecipeToMatterBall} 均调用本方法）。
 */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu", remap = false)
public class BdBucketCraftTransferMixin {

    @Inject(method = "extractFromStorage", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void beyond$fluidFallback(IStackHandler storage, IStackKey<?> type, int amount,
                                      CallbackInfoReturnable<Integer> cir) {
        try {
            Integer remaining = cir.getReturnValue();
            if (remaining == null || remaining <= 0) return;
            if (!(type instanceof ItemStackKey isk)) return;
            long sub = BucketFluidHelper.substituteWithFluid(storage, isk.getReadOnlyStack(), remaining);
            if (sub <= 0L) return;
            cir.setReturnValue((int) Math.max(0L, remaining - sub));
        } catch (Throwable ignored) {}
    }
}
