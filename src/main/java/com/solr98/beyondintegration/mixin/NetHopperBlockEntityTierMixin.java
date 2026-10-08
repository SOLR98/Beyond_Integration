package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.hopper.HopperTierAccess;
import com.solr98.beyondintegration.feature.hopper.HopperTierLogic;
import com.solr98.beyondintegration.feature.magnet.MagnetSettings;
import com.solr98.beyondintegration.feature.magnet.MagnetTier;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetHopperBlockEntity;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 维度网络漏斗（{@code net_hopper_block}）的 BI 档位改造：物品/经验用物品档位、流体用流体档位，
 * 半径与执行间隔各自独立（复用磁铁 {@link com.solr98.beyondintegration.feature.magnet.MagnetTiers} 配置）；
 * 每方块选择存进方块实体 NBT（{@code bi_hopper_tier_item}/{@code bi_hopper_tier_fluid}）。
 */
@Mixin(value = NetHopperBlockEntity.class, remap = false)
public abstract class NetHopperBlockEntityTierMixin implements HopperTierAccess {

    @Shadow(remap = false)
    private StackHandler filterSlots;

    @Unique private int beyond$tierItem = -1;
    @Unique private int beyond$tierFluid = -1;
    @Unique private List<ItemEntity> beyond$itemCache = new ArrayList<>();
    @Unique private List<ExperienceOrb> beyond$xpCache = new ArrayList<>();

    @Override
    public int beyond$getItemTier() {
        return beyond$tierItem;
    }

    @Override
    public void beyond$setItemTier(int value) {
        beyond$tierItem = value;
    }

    @Override
    public int beyond$getFluidTier() {
        return beyond$tierFluid;
    }

    @Override
    public void beyond$setFluidTier(int value) {
        beyond$tierFluid = value;
    }

    @Unique private int beyond$effectiveItem() {
        return beyond$tierItem >= 0 ? beyond$tierItem : MagnetSettings.DEFAULT_INDEX;
    }

    @Unique private int beyond$effectiveFluid() {
        return beyond$tierFluid >= 0 ? beyond$tierFluid : MagnetSettings.DEFAULT_INDEX;
    }

    @Overwrite(remap = false)
    public int getTicksPerWork() {
        NetHopperBlockEntity self = (NetHopperBlockEntity) (Object) this;
        return HopperTierLogic.ticksPerWork(self.hopperItemMode, self.hopperXpMode, self.hopperFluidMode,
                beyond$effectiveItem(), beyond$effectiveFluid());
    }

    @Overwrite(remap = false)
    public void workStart() {
        NetHopperBlockEntity self = (NetHopperBlockEntity) (Object) this;
        MagnetTier tier = HopperTierLogic.itemTier(beyond$effectiveItem());
        if (HopperTierLogic.itemEnabled(self.hopperItemMode, self.hopperXpMode)
                && HopperTierLogic.due(tier.interval(), self.getLevel().getGameTime())) {
            AABB area = HopperTierLogic.searchArea(self.getLevel(), self.getBlockPos(), tier);
            beyond$itemCache = self.hopperItemMode == HopperItemMode.ALLOW
                    ? HopperTierLogic.refreshItems(self.getLevel(), area, self.hopperNBTMode) : new ArrayList<>();
            beyond$xpCache = self.hopperXpMode == HopperXpMode.ALLOW
                    ? HopperTierLogic.refreshXp(self.getLevel(), area) : new ArrayList<>();
        } else {
            beyond$itemCache = new ArrayList<>();
            beyond$xpCache = new ArrayList<>();
        }
    }

    @Overwrite(remap = false)
    public void workContent() {
        NetHopperBlockEntity self = (NetHopperBlockEntity) (Object) this;
        UnifiedStorage storage = self.getNet().getUnifiedStorage();

        if (self.hopperItemMode == HopperItemMode.ALLOW) {
            HopperTierLogic.collectItems(storage, beyond$itemCache, self.filterMode, filterSlots);
            beyond$itemCache = new ArrayList<>();
        }
        if (self.hopperXpMode == HopperXpMode.ALLOW) {
            HopperTierLogic.collectXp(storage, beyond$xpCache);
            beyond$xpCache = new ArrayList<>();
        }
        MagnetTier fluidTier = HopperTierLogic.fluidTier(beyond$effectiveFluid());
        if (self.hopperFluidMode == HopperFluidMode.ALLOW
                && HopperTierLogic.due(fluidTier.interval(), self.getLevel().getGameTime())) {
            AABB area = HopperTierLogic.searchArea(self.getLevel(), self.getBlockPos(), fluidTier);
            HopperTierLogic.fluidCollect(storage, self.getLevel(), area, self.filterMode, filterSlots);
        }
    }

    @Inject(method = "load", at = @At("TAIL"), remap = false)
    private void beyond$readTiers(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("bi_hopper_tier_item")) {
            beyond$tierItem = tag.getInt("bi_hopper_tier_item");
        }
        if (tag.contains("bi_hopper_tier_fluid")) {
            beyond$tierFluid = tag.getInt("bi_hopper_tier_fluid");
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void beyond$writeTiers(CompoundTag tag, CallbackInfo ci) {
        tag.putInt("bi_hopper_tier_item", beyond$tierItem);
        tag.putInt("bi_hopper_tier_fluid", beyond$tierFluid);
    }
}
