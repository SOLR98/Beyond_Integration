package com.solr98.beyondintegration.feature.xp;

import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 经验棒物品上的自动修复设置（NBT）。
 * <p>分工：服务端配置决定修复模式 / 档位列表 / 修复数值，
 * 经验棒界面上的按钮决定“实际使用”（开关 / 档位索引 / 修复范围），按物品保存并随菜单 QuickData 同步。
 */
public final class XpRepairSettings {

    /** 经验棒界面开关按钮状态（BD StatusButton 的 Enum 状态）。 */
    public enum ToggleState { ON, OFF }

    private static final String TAG_ENABLED = "beyond_integration:xp_repair_enabled";
    /** 档位索引（对应服务端 xp_repair_tiers 列表）。 */
    private static final String TAG_TIER_INDEX = "beyond_integration:xp_repair_tier_index";
    private static final String TAG_RANGE_ARMOR = "beyond_integration:xp_repair_range_armor";
    private static final String TAG_RANGE_INVENTORY = "beyond_integration:xp_repair_range_inventory";
    private static final String TAG_RANGE_HANDS = "beyond_integration:xp_repair_range_hands";
    private static final String TAG_RANGE_CURIOS = "beyond_integration:xp_repair_range_curios";
    private static final String TAG_RANGE_MAID = "beyond_integration:xp_repair_range_maid";

    private XpRepairSettings() {}

    // ── 开关（默认开启：物品上无 NBT 时视为开）──

    public static boolean isEnabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = com.solr98.beyondintegration.core.util.ItemNbt.readOrNull(stack);
        return tag == null || !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
    }

    public static void setEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || stack.isEmpty()) return;
        com.solr98.beyondintegration.core.util.ItemNbt.update(stack, t -> t.putBoolean(TAG_ENABLED, enabled));
    }

    // ── 修复范围（默认全部开启）──

    public static boolean isArmorEnabled(ItemStack stack) { return getRange(stack, TAG_RANGE_ARMOR); }
    public static void setArmorEnabled(ItemStack stack, boolean v) { setRange(stack, TAG_RANGE_ARMOR, v); }

    public static boolean isInventoryEnabled(ItemStack stack) { return getRange(stack, TAG_RANGE_INVENTORY); }
    public static void setInventoryEnabled(ItemStack stack, boolean v) { setRange(stack, TAG_RANGE_INVENTORY, v); }

    public static boolean isHandsEnabled(ItemStack stack) { return getRange(stack, TAG_RANGE_HANDS); }
    public static void setHandsEnabled(ItemStack stack, boolean v) { setRange(stack, TAG_RANGE_HANDS, v); }

    public static boolean isCuriosEnabled(ItemStack stack) { return getRange(stack, TAG_RANGE_CURIOS); }
    public static void setCuriosEnabled(ItemStack stack, boolean v) { setRange(stack, TAG_RANGE_CURIOS, v); }

    public static boolean isMaidEnabled(ItemStack stack) { return getRange(stack, TAG_RANGE_MAID); }
    public static void setMaidEnabled(ItemStack stack, boolean v) { setRange(stack, TAG_RANGE_MAID, v); }


    private static boolean getRange(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = com.solr98.beyondintegration.core.util.ItemNbt.readOrNull(stack);
        return tag == null || !tag.contains(key) || tag.getBoolean(key);
    }

    private static void setRange(ItemStack stack, String key, boolean v) {
        if (stack == null || stack.isEmpty()) return;
        com.solr98.beyondintegration.core.util.ItemNbt.update(stack, t -> t.putBoolean(key, v));
    }

    // ── 档位索引（对应服务端 xp_repair_tiers 列表）──

    public static int getTierIndex(ItemStack stack) {
        int fallback = XpRepairTiers.defaultIndex();
        if (stack == null || stack.isEmpty()) return fallback;
        CompoundTag tag = com.solr98.beyondintegration.core.util.ItemNbt.readOrNull(stack);
        if (tag == null) return fallback;
        if (tag.contains(TAG_TIER_INDEX)) {
            return clampIndex(tag.getInt(TAG_TIER_INDEX));
        }
        return fallback;
    }

    public static void setTierIndex(ItemStack stack, int index) {
        if (stack == null || stack.isEmpty()) return;
        com.solr98.beyondintegration.core.util.ItemNbt.update(stack, tag -> tag.putInt(TAG_TIER_INDEX, clampIndex(index)));
    }

    /** 越界收敛到当前档位列表范围。 */
    private static int clampIndex(int index) {
        int count = XpRepairTiers.count();
        if (count <= 0) return 0;
        return Math.max(0, Math.min(index, count - 1));
    }


    // ── 快速数据同步（QuickData）──

    /** 把开关 / 档位 / 各修复范围写入快速数据标签。 */
    public static void writeToTag(ItemStack stack, CompoundTag tag) {
        if (tag == null) return;
        tag.putBoolean(TAG_ENABLED, isEnabled(stack));
        tag.putInt(TAG_TIER_INDEX, getTierIndex(stack));
        tag.putBoolean(TAG_RANGE_ARMOR, isArmorEnabled(stack));
        tag.putBoolean(TAG_RANGE_INVENTORY, isInventoryEnabled(stack));
        tag.putBoolean(TAG_RANGE_HANDS, isHandsEnabled(stack));
        tag.putBoolean(TAG_RANGE_CURIOS, isCuriosEnabled(stack));
        tag.putBoolean(TAG_RANGE_MAID, isMaidEnabled(stack));
    }

    /** 从快速数据标签读回设置并写回物品 NBT。 */
    public static void readFromTag(ItemStack stack, CompoundTag tag) {
        if (tag == null || stack == null || stack.isEmpty()) return;
        if (tag.contains(TAG_ENABLED)) setEnabled(stack, tag.getBoolean(TAG_ENABLED));
        if (tag.contains(TAG_TIER_INDEX)) setTierIndex(stack, tag.getInt(TAG_TIER_INDEX));
        if (tag.contains(TAG_RANGE_ARMOR)) setArmorEnabled(stack, tag.getBoolean(TAG_RANGE_ARMOR));
        if (tag.contains(TAG_RANGE_INVENTORY)) setInventoryEnabled(stack, tag.getBoolean(TAG_RANGE_INVENTORY));
        if (tag.contains(TAG_RANGE_HANDS)) setHandsEnabled(stack, tag.getBoolean(TAG_RANGE_HANDS));
        if (tag.contains(TAG_RANGE_CURIOS)) setCuriosEnabled(stack, tag.getBoolean(TAG_RANGE_CURIOS));
        if (tag.contains(TAG_RANGE_MAID)) setMaidEnabled(stack, tag.getBoolean(TAG_RANGE_MAID));
    }

    /** BD 图集精灵纹理 ID（按钮图标）。 */
    public static ResourceLocation icon(String name) {
        return ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/" + name + ".png");
    }

    /** BI 自有纹理 ID（按钮图标，纹理位于 assets/beyond_integration/textures/gui/）。 */
    public static ResourceLocation biIcon(String name) {
        return ResourceLocation.tryBuild("beyond_integration", "textures/gui/" + name + ".png");
    }
}
