package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.NetFurnaceWaterPurify;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 网络熔炉「输入存储槽」放行水容器：同 {@link NetFurnaceInputFilterWaterMixin}，
 * 使带 Thirst 纯度的水瓶/水桶能真正放进熔炉输入槽并参与熔炼。
 */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity$3", remap = false)
public class NetFurnaceInputStorageWaterMixin {

    @Inject(method = "isStackValid", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$acceptWater(int slot, IStackKey<?> key, CallbackInfoReturnable<Boolean> cir) {
        if (NetFurnaceWaterPurify.isFilledWaterContainer(key)) cir.setReturnValue(true);
    }
}
