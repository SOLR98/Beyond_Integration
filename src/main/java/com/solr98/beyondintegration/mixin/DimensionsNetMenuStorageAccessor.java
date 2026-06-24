package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu", remap = false)
public interface DimensionsNetMenuStorageAccessor {

    @Accessor("storage")
    AbstractUnorderedStackHandler getStorage();
}
