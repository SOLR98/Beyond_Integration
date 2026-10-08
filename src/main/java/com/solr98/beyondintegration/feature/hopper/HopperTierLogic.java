package com.solr98.beyondintegration.feature.hopper;

import com.solr98.beyondintegration.feature.magnet.MagnetTier;
import com.solr98.beyondintegration.feature.magnet.MagnetTiers;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.util.ItemStackHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.List;

/**
 * 维度网络漏斗的 BI 档位逻辑（1.21.1）：物品/经验用物品档位、流体用流体档位，各自独立节拍。
 */
public final class HopperTierLogic {

    private HopperTierLogic() {}

    public static MagnetTier itemTier(int index) {
        return MagnetTiers.itemByIndex(index);
    }

    public static MagnetTier fluidTier(int index) {
        return MagnetTiers.fluidByIndex(index);
    }

    public static boolean itemEnabled(HopperItemMode itemMode, HopperXpMode xpMode) {
        return itemMode == HopperItemMode.ALLOW || xpMode == HopperXpMode.ALLOW;
    }

    public static int ticksPerWork(HopperItemMode itemMode, HopperXpMode xpMode, HopperFluidMode fluidMode,
                                   int itemIndex, int fluidIndex) {
        boolean itemOn = itemEnabled(itemMode, xpMode);
        boolean fluidOn = fluidMode == HopperFluidMode.ALLOW;
        int gi = itemTier(itemIndex).interval();
        int gf = fluidTier(fluidIndex).interval();
        if (itemOn && fluidOn) {
            if (gi <= 0 || gf <= 0) return 1;
            return gcd(gi, gf);
        }
        if (itemOn) return gi;
        if (fluidOn) return gf;
        return 0;
    }

    public static boolean due(int interval, long time) {
        return interval <= 0 || time % interval == 0;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    public static AABB searchArea(Level level, BlockPos pos, MagnetTier tier) {
        if (!tier.chunk()) {
            int radius = tier.radius();
            return new AABB(
                    pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
                    pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius);
        }
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return new AABB(minX, level.getMinBuildHeight(), minZ, minX + 15, level.getMaxBuildHeight(), minZ + 15);
    }

    public static List<ItemEntity> refreshItems(Level level, AABB area, HopperNBTMode nbtMode) {
        return level.getEntitiesOfClass(ItemEntity.class, area,
                e -> nbtMode != HopperNBTMode.DENY || !ItemStackHelper.hasExtraComponents(e.getItem()));
    }

    public static List<ExperienceOrb> refreshXp(Level level, AABB area) {
        return level.getEntitiesOfClass(ExperienceOrb.class, area, o -> true);
    }

    public static void collectItems(UnifiedStorage storage, List<ItemEntity> list, FilterMode filterMode, IStackHandler filterSlots) {
        for (ItemEntity itemEntity : list) {
            if (itemEntity == null || itemEntity.isRemoved()) {
                continue;
            }
            ItemStack itemStack = itemEntity.getItem();
            IStackKey<?> itemKey = new ItemStackKey(itemStack);
            if (!matchesFilter(filterMode, filterSlots, itemKey)) {
                continue;
            }
            if (storage.insert(itemKey, itemStack.getCount(), true).isEmpty()) {
                itemEntity.discard();
                storage.insert(itemKey, itemStack.getCount(), false);
            }
        }
    }

    public static void collectXp(UnifiedStorage storage, List<ExperienceOrb> list) {
        FluidStackKey xpKey = new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source(), 1));
        for (ExperienceOrb orb : list) {
            if (orb == null || orb.isRemoved()) {
                continue;
            }
            int xp = orb.getValue();
            if (xp <= 0) {
                continue;
            }
            long xpFluid = xp * 20L;
            if (storage.insert(xpKey, xpFluid, true).isEmpty()) {
                orb.discard();
                storage.insert(xpKey, xpFluid, false);
            }
        }
    }

    public static void fluidCollect(UnifiedStorage storage, Level level, AABB area, FilterMode filterMode, IStackHandler filterSlots) {
        if (level == null || level.isClientSide) {
            return;
        }
        int minX = Mth.floor(area.minX), minY = Mth.floor(area.minY), minZ = Mth.floor(area.minZ);
        int maxX = Mth.floor(area.maxX), maxY = Mth.floor(area.maxY), maxZ = Mth.floor(area.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    pos.set(x, y, z);
                    FluidState fluidState = level.getFluidState(pos);
                    if (fluidState.isEmpty()) {
                        continue;
                    }
                    int amount = fluidState.isSource() ? FluidType.BUCKET_VOLUME : 0;
                    FluidStackKey fluidKey = new FluidStackKey(new FluidStack(fluidState.getType(), amount));
                    if (!matchesFilter(filterMode, filterSlots, fluidKey)) {
                        continue;
                    }
                    if (storage.insert(fluidKey, amount, true).isEmpty()) {
                        storage.insert(fluidKey, amount, false);
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof BucketPickup pickup && !(state.getBlock() instanceof LiquidBlock)) {
                            pickup.pickupBlock(null, level, pos, state);
                        } else {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                        }
                    }
                }
            }
        }
    }

    private static boolean matchesFilter(FilterMode filterMode, IStackHandler filterSlots, IStackKey<?> other) {
        switch (filterMode) {
            case BLACK -> {
                for (KeyAmount ka : filterSlots.getStorage()) {
                    if (ka.key().isSame(other)) {
                        return false;
                    }
                }
                return true;
            }
            case WHITE -> {
                for (KeyAmount ka : filterSlots.getStorage()) {
                    if (ka.key().isSame(other)) {
                        return true;
                    }
                }
                return false;
            }
            case IGNORE -> {
                return true;
            }
        }
        return false;
    }
}
