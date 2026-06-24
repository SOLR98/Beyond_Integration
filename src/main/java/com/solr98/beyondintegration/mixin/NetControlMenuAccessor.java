package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.NetControlMenu", remap = false)
public interface NetControlMenuAccessor {

    @Accessor("net")
    DimensionsNet getNet();
}
