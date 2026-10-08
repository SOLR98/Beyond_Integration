package com.solr98.beyondintegration.feature.magnet;

import net.minecraft.world.item.ItemStack;

/**
 * 网络磁铁每物品设置：物品吸取档位索引与流体吸取档位索引（各一套，互不影响）。
 * <p>键名加 {@code bi_} 前缀；{@code <0} 表示未设置（回退默认档 {@link #DEFAULT_INDEX}）。
 */
public final class MagnetSettings {

    /** 物品吸取档位索引键 */
    public static final String KEY_TIER_ITEM = "bi_magnet_tier_item";
    /** 流体吸取档位索引键 */
    public static final String KEY_TIER_FLUID = "bi_magnet_tier_fluid";
    /** 未设置时的默认档位索引（mid） */
    public static final int DEFAULT_INDEX = 2;

    private MagnetSettings() {}

    public static int getItemTierIndex(ItemStack stack) {
        if (stack != null && stack.hasTag() && stack.getTag().contains(KEY_TIER_ITEM)) {
            return stack.getTag().getInt(KEY_TIER_ITEM);
        }
        return -1;
    }

    public static void setItemTierIndex(ItemStack stack, int index) {
        if (stack == null) return;
        stack.getOrCreateTag().putInt(KEY_TIER_ITEM, index);
    }

    public static int getFluidTierIndex(ItemStack stack) {
        if (stack != null && stack.hasTag() && stack.getTag().contains(KEY_TIER_FLUID)) {
            return stack.getTag().getInt(KEY_TIER_FLUID);
        }
        return -1;
    }

    public static void setFluidTierIndex(ItemStack stack, int index) {
        if (stack == null) return;
        stack.getOrCreateTag().putInt(KEY_TIER_FLUID, index);
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
