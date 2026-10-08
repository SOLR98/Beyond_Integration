package com.solr98.beyondintegration.feature.feeder;

import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * 网络熔炉「自动组装水瓶/水桶」执行器（配合 Thirst was Reclaimed 的 NBT 提纯配方）。
 * <p>
 * BD 网络熔炉的输入槽原本只接受「能匹配到熔炼配方」的物品，而 Thirst 提纯吃的是带 {@code Purity} 的水瓶/水桶，
 * 直接放不进去。这里按输入标记槽（放空瓶/空桶）从网络取「水流体 + 空容器」，组装出带纯度的水容器放入输入槽；
 * 熔炉随后的原生物品熔炼即可命中 Thirst 的 smelting 配方，产物按收纳设置回网络。
 * 仅当装载了 Thirst（或 LSO，经 {@link ThirstBridge}）时生效。
 */
public final class NetFurnaceWaterPurify {

    private NetFurnaceWaterPurify() {}

    /** 按标记槽组装水容器并填入空闲输入槽。 */
    public static void assemble(BaseNetFurnaceBlockEntity<?> furnace) {
        if (!ThirstBridge.get().loaded()) return;
        Level level = furnace.getLevel();
        if (level == null || level.isClientSide()) return;
        var net = furnace.getNet();
        if (net == null) return;
        UnifiedStorage storage = net.getUnifiedStorage();
        if (storage == null) return;

        StackHandler inputs = furnace.getInputStorageSlots();
        StackHandler filters = furnace.getInputFilterSlots();
        int slots = inputs.getSlots();

        for (int i = 0; i < slots; i++) {
            if (!inputs.getStackBySlot(i).isEmpty()) continue;

            for (KeyAmount mark : filters.getStorage()) {
                if (!(mark.key() instanceof ItemStackKey markItem)) continue;
                ItemStack marker = markItem.getReadOnlyStack();

                Boolean asBucket = filledContainerKind(marker);
                if (asBucket == null) continue;

                // 标记槽放的是“待提纯的满容器”，按其容器类型与纯度组装
                int wantPurity = ThirstBridge.get().readPurity(marker);
                ItemStack filled = tryAssemble(storage, level, asBucket, wantPurity);
                if (filled.isEmpty()) continue;

                inputs.setStackDirectly(i, new ItemStackKey(filled), 1L);
                break;
            }
        }
    }

    /** 标记槽须为“待提纯的满水容器”：TRUE=水桶、FALSE=水瓶、null=非水满容器。 */
    private static Boolean filledContainerKind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.WATER_BUCKET)) return Boolean.TRUE;
        return isWaterPotion(stack) ? Boolean.FALSE : null;
    }

    /** 从网络水组装一份水容器（不可用返回 EMPTY）。{@code wantPurity} 为 -1 表示标记未指定纯度。 */
    private static ItemStack tryAssemble(UnifiedStorage storage, Level level, boolean asBucket, int wantPurity) {
        ItemStackKey emptyKey = new ItemStackKey(
                asBucket ? new ItemStack(Items.BUCKET) : new ItemStack(Items.GLASS_BOTTLE));
        long per = asBucket ? BucketFluidHelper.MB_PER_BUCKET : BucketFluidHelper.MB_PER_BOTTLE;

        // 目标纯度：以标记槽（待提纯满容器）为准；标记未标注纯度时才取网络水自身纯度
        int desired = wantPurity;
        FluidStackKey chosen = null;
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof FluidStackKey fk) || !BucketFluidHelper.isWaterFluid(fk)) continue;
            int fp = BucketFluidHelper.waterPurity(fk);
            if (desired < 0) {
                if (fp >= 0 && fp <= 2) { chosen = fk; desired = fp; break; }
            } else if (fp == desired) {
                chosen = fk; break;
            }
        }
        if (chosen == null && desired >= 0) {
            for (KeyAmount ka : storage.getStorage()) {
                if (ka.key() instanceof FluidStackKey fk && BucketFluidHelper.isWaterFluid(fk)) { chosen = fk; break; }
            }
        }
        if (chosen == null || desired < 0 || desired > 2) return ItemStack.EMPTY;

        ItemStack candidate = buildCandidate(asBucket, desired);
        if (candidate.isEmpty()) return ItemStack.EMPTY;
        if (level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(candidate), level)
                .isEmpty()) return ItemStack.EMPTY;

        if (storage.getStackByKey(emptyKey).amount() < 1L) return ItemStack.EMPTY;
        if (storage.getStackByKey(chosen).amount() < per) return ItemStack.EMPTY;

        ItemStack out;
        if (asBucket) {
            out = BucketFluidHelper.fillBuckets(storage, chosen, 1);
        } else {
            storage.extract(emptyKey, 1L, false, false);
            out = BucketFluidHelper.fillWaterBottles(storage, chosen, 1);
            if (out.isEmpty()) { storage.insert(emptyKey, 1L, false); return ItemStack.EMPTY; }
        }
        if (out.isEmpty()) return ItemStack.EMPTY;
        // 以标记纯度为准确认（BucketFluidHelper 依据流体推导的纯度可能与标记不一致）
        ThirstBridge.get().tagPurity(out, desired);
        return out;
    }

    /** 构造带纯度的水容器（与 {@link BucketFluidHelper} 的产出保持一致）。 */
    private static ItemStack buildCandidate(boolean asBucket, int purity) {
        ItemStack stack;
        if (asBucket) {
            stack = new ItemStack(Items.WATER_BUCKET);
        } else {
            stack = new ItemStack(Items.POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        }
        ThirstBridge.get().tagPurity(stack, purity);
        return stack;
    }

    /** 是否为可被网络熔炉接受的「已灌水容器」（供输入槽校验放行）。 */
    public static boolean isFilledWaterContainer(Object key) {
        if (!ThirstBridge.get().loaded()) return false;
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        if (stack.isEmpty()) return false;
        return stack.is(Items.WATER_BUCKET) || isWaterPotion(stack);
    }

    private static boolean isWaterPotion(ItemStack stack) {
        if (stack == null || !stack.is(Items.POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.potion().map(h -> h.is(Potions.WATER)).orElse(false);
    }
}
