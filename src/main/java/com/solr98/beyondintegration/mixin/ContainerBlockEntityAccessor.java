package com.solr98.beyondintegration.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity", remap = false)
public interface ContainerBlockEntityAccessor {

    @Accessor("entityTag")
    CompoundTag getEntityTag();
}
