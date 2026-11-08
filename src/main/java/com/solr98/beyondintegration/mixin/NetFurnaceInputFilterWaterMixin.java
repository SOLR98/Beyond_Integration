package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.NetFurnaceWaterPurify;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 网络熔炉「输入标记槽」放行水容器：
 * BD 默认只接受能匹配熔炼配方的物品，导致带 Thirst 纯度的水瓶/水桶放不进去。
 * Thirst 装载时放行水桶/水瓶（含水瓶）。
 */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity$1", remap = false)
public class NetFurnaceInputFilterWaterMixin {

    @Inject(method = "isStackValid", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$acceptWater(int slot, IStackKey<?> key, CallbackInfoReturnable<Boolean> cir) {
        if (NetFurnaceWaterPurify.isFilledWaterContainer(key)) cir.setReturnValue(true);
    }
}
