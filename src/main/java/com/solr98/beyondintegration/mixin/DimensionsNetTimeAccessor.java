package com.solr98.beyondintegration.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public interface DimensionsNetTimeAccessor {

    @Accessor("currentTime")
    int getCurrentTime();
}
