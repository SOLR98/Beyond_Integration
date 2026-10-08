package com.solr98.beyondintegration.feature.magnet;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.util.ItemStackHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络磁铁（{@code net_magnet_item}）的 BI 版实现（1.21.1）：从 BD {@code NetMagnetItem} 复制而来，
 * 把“吸取范围/间隔”改为读取 BI 自定义档位列表（{@link MagnetTiers}）。
 * <p><b>物品吸取与流体吸取分开执行</b>：物品/经验用物品档位，流体用流体档位，各自到点才跑
 * （以 {@code level.getGameTime()} 对齐，{@code getTicksPerWork} 取二者间隔的 gcd）。
 * 由 {@code NetMagnetItemMixin} 以 {@code @Overwrite} 接管 BD 原实现。
 */
public final class MagnetHandler {

    private MagnetHandler() {}

    /** 每个工作周期执行：按各自档位与节拍收集掉落物 / 经验球 / 源流体入网。 */
    public static void handle(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected) {
        if (level.isClientSide() || NetedItem.getNet(stack) == null) {
            return;
        }

        FilterMode filterMode = stack.getOrDefault(BDDataComponents.FILTER_MODE, FilterMode.BLACK);
        HopperItemMode hopperItemMode = stack.getOrDefault(BDDataComponents.HOPPER_ITEM_MODE, HopperItemMode.ALLOW);
        HopperXpMode hopperXpMode = stack.getOrDefault(BDDataComponents.HOPPER_XP_MODE, HopperXpMode.DENY);
        HopperNBTMode hopperNBTMode = stack.getOrDefault(BDDataComponents.HOPPER_NBT_MODE, HopperNBTMode.DENY);
        HopperFluidMode hopperFluidMode = stack.getOrDefault(BDDataComponents.HOPPER_FLUID_MODE, HopperFluidMode.DENY);
        List<KeyAmount> filterSlots = stack.getOrDefault(BDDataComponents.ISTACK_SLOTS, new ArrayList<>());

        MagnetTier itemTier = MagnetTiers.itemByIndex(MagnetSettings.effectiveItemTier(stack));
        MagnetTier fluidTier = MagnetTiers.fluidByIndex(MagnetSettings.effectiveFluidTier(stack));

        UnifiedStorage storage = NetedItem.getNet(stack).getUnifiedStorage();
        Vec3i pos = holder.getOnPos();
        long time = level.getGameTime();
        boolean itemReady = due(itemTier.interval(), time);
        boolean fluidReady = due(fluidTier.interval(), time);

        if (hopperItemMode == HopperItemMode.ALLOW && itemReady) {
            AABB area = getSearchArea(itemTier, level, pos);
            collectItems(storage, filterMode, filterSlots, refreshItemEntityCache(hopperNBTMode, level, area), holder);
        }
        if (hopperXpMode == HopperXpMode.ALLOW && itemReady) {
            AABB area = getSearchArea(itemTier, level, pos);
            collectXp(storage, level, area);
        }
        if (hopperFluidMode == HopperFluidMode.ALLOW && fluidReady) {
            AABB area = getSearchArea(fluidTier, level, pos);
            fluidCollect(filterMode, filterSlots, storage, level, area);
        }
    }

    /** 物品与流体都启用时取二者间隔的 gcd，使两者“到点”都能被 workContent 覆盖。 */
    public static int ticksPerWork(ItemStack stack) {
        HopperItemMode im = stack.getOrDefault(BDDataComponents.HOPPER_ITEM_MODE, HopperItemMode.ALLOW);
        HopperXpMode xm = stack.getOrDefault(BDDataComponents.HOPPER_XP_MODE, HopperXpMode.DENY);
        HopperFluidMode fm = stack.getOrDefault(BDDataComponents.HOPPER_FLUID_MODE, HopperFluidMode.DENY);
        boolean itemOn = im == HopperItemMode.ALLOW || xm == HopperXpMode.ALLOW;
        boolean fluidOn = fm == HopperFluidMode.ALLOW;

        int gi = MagnetTiers.itemByIndex(MagnetSettings.effectiveItemTier(stack)).interval();
        int gf = MagnetTiers.fluidByIndex(MagnetSettings.effectiveFluidTier(stack)).interval();

        if (itemOn && fluidOn) {
            if (gi <= 0 || gf <= 0) return 1;
            return gcd(gi, gf);
        }
        if (itemOn) return gi;
        if (fluidOn) return gf;
        return 0;
    }

    private static boolean due(int interval, long time) {
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

    private static void collectItems(UnifiedStorage storage, FilterMode filterMode, List<KeyAmount> filterSlots,
                                     List<ItemEntity> itemEntities, Entity holder) {
        for (ItemEntity itemEntity : itemEntities) {
            if (itemEntity == null || itemEntity.isRemoved()) {
                continue;
            }
            ItemStack itemStack = itemEntity.getItem();
            ItemStackKey itemKey = new ItemStackKey(itemStack);
            if (!matchesFilter(filterMode, filterSlots, itemKey)) {
                continue;
            }
            int count = itemStack.getCount();
            if (!storage.insert(itemKey, count, true).isEmpty()) {
                continue;
            }
            if (holder instanceof Player player) {
                ItemStack originalCopy = itemStack.copy();
                itemStack.setCount(0);
                EventHooks.fireItemPickupPost(itemEntity, player, originalCopy);
                itemStack.setCount(count);
                player.awardStat(Stats.ITEM_PICKED_UP.get(originalCopy.getItem()), count);
                player.onItemPickup(itemEntity);
            }
            itemEntity.discard();
            storage.insert(itemKey, count, false);
        }
    }

    private static void collectXp(UnifiedStorage storage, Level level, AABB area) {
        FluidStackKey xpStack = new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source(), 1));
        for (ExperienceOrb orb : refreshXpEntityCache(level, area)) {
            if (orb == null || orb.isRemoved()) {
                continue;
            }
            int xp = orb.getValue();
            if (xp <= 0) {
                continue;
            }
            long xpFluid = xp * 20L;
            if (storage.insert(xpStack, xpFluid, true).isEmpty()) {
                orb.discard();
                storage.insert(xpStack, xpFluid, false);
            }
        }
    }

    private static AABB getSearchArea(MagnetTier tier, Level level, Vec3i pos) {
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

    private static List<ItemEntity> refreshItemEntityCache(HopperNBTMode nbtMode, Level level, AABB area) {
        return level.getEntitiesOfClass(ItemEntity.class, area,
                e -> nbtMode != HopperNBTMode.DENY || !ItemStackHelper.hasExtraComponents(e.getItem()));
    }

    private static List<ExperienceOrb> refreshXpEntityCache(Level level, AABB area) {
        return level.getEntitiesOfClass(ExperienceOrb.class, area, o -> true);
    }

    private static void fluidCollect(FilterMode filterMode, List<KeyAmount> filterSlots,
                                     UnifiedStorage storage, Level level, AABB area) {
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
                    Fluid stillFluid = fluidState.getType();
                    if (stillFluid instanceof FlowingFluid ff) {
                        stillFluid = ff.getSource();
                    }
                    FluidStackKey fluidKey = new FluidStackKey(new FluidStack(stillFluid, 1));
                    if (!matchesFilter(filterMode, filterSlots, fluidKey)) {
                        continue;
                    }
                    if (!storage.insert(fluidKey, amount, true).isEmpty()) {
                        continue;
                    }
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

    private static boolean matchesFilter(FilterMode filterMode, List<KeyAmount> filterSlots, IStackKey<?> other) {
        switch (filterMode) {
            case BLACK -> {
                for (KeyAmount ka : filterSlots) {
                    if (ka.key().isSame(other)) {
                        return false;
                    }
                }
                return true;
            }
            case WHITE -> {
                for (KeyAmount ka : filterSlots) {
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
