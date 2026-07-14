package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.bind.InsertContext;
import com.wintercogs.beyonddimensions.common.block.entity.NetFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetFurnaceBlockEntity", remap = false)
public class NetFurnaceWorkMixin {

    @Inject(method = "workContent", at = @At("HEAD"), remap = false)
    private void beyond$beforeWork(CallbackInfo ci) {
        NetFurnaceBlockEntity be = (NetFurnaceBlockEntity) (Object) this;
        InsertContext.set("FURNACE", be.getBlockPos());
    }

    @Inject(method = "workEnd", at = @At("RETURN"), remap = false)
    private void beyond$afterWorkEnd(CallbackInfo ci) {
        InsertContext.clear();
    }
}
