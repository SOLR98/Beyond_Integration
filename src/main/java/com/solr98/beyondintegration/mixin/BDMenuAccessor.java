package com.solr98.beyondintegration.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/**
 * Accessor 注入：为 BeyondDimensions 的 DimensionsCraftMenuTerminal 暴露私有字段
 * entityPos（合成终端对应的实体坐标），供本模组在服务端定位终端网络。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenuTerminal.class, remap = false)
public interface BDMenuAccessor {

    /** 暴露终端菜单私有字段 entityPos：终端实体所在坐标（可为空） */
    @Accessor("entityPos")
    @Nullable
    BlockPos getEntityPos();
}
