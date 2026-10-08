package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.hopper.HopperTierAccess;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import com.wintercogs.beyonddimensions.common.menu.NetHopperMenu;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 维度网络漏斗的 BI 档位同步（1.21.1）。
 */
@Mixin(value = NetHopperMenu.class, remap = false)
public class NetHopperMenuTierMixin {

    @Shadow(remap = false)
    public NetHopperBlockEntity be;

    @Inject(method = "writeQuickDataTag", at = @At("TAIL"))
    private void beyond$writeTiers(CompoundTag tag, CallbackInfo ci) {
        if (be instanceof HopperTierAccess access) {
            tag.putInt("bi_hopper_tier_item", access.beyond$getItemTier());
            tag.putInt("bi_hopper_tier_fluid", access.beyond$getFluidTier());
        }
    }

    @Inject(method = "readQuickDataTag", at = @At("TAIL"))
    private void beyond$readTiers(CompoundTag tag, CallbackInfo ci) {
        if (be instanceof HopperTierAccess access) {
            if (tag.contains("bi_hopper_tier_item")) {
                access.beyond$setItemTier(tag.getInt("bi_hopper_tier_item"));
            }
            if (tag.contains("bi_hopper_tier_fluid")) {
                access.beyond$setFluidTier(tag.getInt("bi_hopper_tier_fluid"));
            }
            if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                be.getLevel().blockEntityChanged(be.getBlockPos());
                be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
            }
        }
    }
}
