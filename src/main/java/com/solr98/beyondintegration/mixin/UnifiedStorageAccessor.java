package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage", remap = false)
public interface UnifiedStorageAccessor {
    @Accessor("net")
    DimensionsNet beyond$getNet();
}
