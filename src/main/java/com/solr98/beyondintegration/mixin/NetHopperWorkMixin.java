package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.bind.InsertContext;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity", remap = false)
public class NetHopperWorkMixin {

    @Inject(method = "workContent", at = @At("HEAD"), remap = false)
    private void beyond$beforeWork(CallbackInfo ci) {
        NetHopperBlockEntity be = (NetHopperBlockEntity) (Object) this;
        InsertContext.set("HOPPER", be.getBlockPos());
    }

    @Inject(method = "workContent", at = @At("RETURN"), remap = false)
    private void beyond$afterWork(CallbackInfo ci) {
        InsertContext.clear();
    }
}
