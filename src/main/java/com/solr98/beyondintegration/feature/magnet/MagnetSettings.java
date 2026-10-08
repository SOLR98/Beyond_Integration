package com.solr98.beyondintegration.feature.magnet;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * 网络磁铁每物品设置（1.21.1，{@code CUSTOM_DATA}）：物品吸取档位索引与流体吸取档位索引。
 * <p>键名加 {@code bi_} 前缀；{@code <0} 表示未设置（回退默认档 {@link #DEFAULT_INDEX}）。
 */
public final class MagnetSettings {

    public static final String KEY_TIER_ITEM = "bi_magnet_tier_item";
    public static final String KEY_TIER_FLUID = "bi_magnet_tier_fluid";
    public static final int DEFAULT_INDEX = 2;

    private MagnetSettings() {}

    private static CompoundTag read(ItemStack stack) {
        if (stack == null) {
            return new CompoundTag();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void update(ItemStack stack, Consumer<CompoundTag> op) {
        if (stack == null) return;
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(op));
    }

    public static int getItemTierIndex(ItemStack stack) {
        CompoundTag tag = read(stack);
        return tag.contains(KEY_TIER_ITEM) ? tag.getInt(KEY_TIER_ITEM) : -1;
    }

    public static void setItemTierIndex(ItemStack stack, int index) {
        update(stack, tag -> tag.putInt(KEY_TIER_ITEM, index));
    }

    public static int getFluidTierIndex(ItemStack stack) {
        CompoundTag tag = read(stack);
        return tag.contains(KEY_TIER_FLUID) ? tag.getInt(KEY_TIER_FLUID) : -1;
    }

    public static void setFluidTierIndex(ItemStack stack, int index) {
        update(stack, tag -> tag.putInt(KEY_TIER_FLUID, index));
    }

    /** 取实际生效的物品档位索引（未设置回退默认）。 */
    public static int effectiveItemTier(ItemStack stack) {
        int v = getItemTierIndex(stack);
        return v >= 0 ? v : DEFAULT_INDEX;
    }

    /** 取实际生效的流体档位索引（未设置回退默认）。 */
    public static int effectiveFluidTier(ItemStack stack) {
        int v = getFluidTierIndex(stack);
        return v >= 0 ? v : DEFAULT_INDEX;
    }
}
