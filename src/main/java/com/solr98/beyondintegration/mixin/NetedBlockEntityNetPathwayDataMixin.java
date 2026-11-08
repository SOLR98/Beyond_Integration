package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 维度网络通道过滤数据的持久化（1.21.1）：loadAdditional/saveAdditional 末尾读写，
 * 仅当方块实体为 net_pathway（实现了 {@link NetPathwayFilterAccess}）。
 */
@Mixin(value = NetedBlockEntity.class, remap = false)
public abstract class NetedBlockEntityNetPathwayDataMixin {

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void beyond$loadFilter(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof NetPathwayFilterAccess access) {
            access.beyond$setFilterEnabled(tag.getBoolean("beyond_filter_enabled"));
            access.beyond$setOnlyInput(tag.getBoolean("beyond_only_input"));
            access.beyond$setFuzzy(tag.getBoolean("beyond_fuzzy"));
            access.beyond$getFilterSlots().deserializeNBT(registries, tag.getCompound("beyond_filter_slots"));
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void beyond$saveFilter(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof NetPathwayFilterAccess access) {
            tag.putBoolean("beyond_filter_enabled", access.beyond$isFilterEnabled());
            tag.putBoolean("beyond_only_input", access.beyond$isOnlyInput());
            tag.putBoolean("beyond_fuzzy", access.beyond$isFuzzy());
            tag.put("beyond_filter_slots", access.beyond$getFilterSlots().serializeNBT(registries));
        }
    }
}
