package com.solr98.beyondintegration.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

@Mixin(value = com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenuTerminal.class, remap = false)
public interface BDMenuAccessor {

    @Accessor("entityPos")
    @Nullable
    BlockPos getEntityPos();
}
