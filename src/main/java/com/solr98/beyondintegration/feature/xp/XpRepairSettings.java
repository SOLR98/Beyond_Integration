package com.solr98.beyondintegration.feature.xp;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 经验棒物品上的自动修复设置（NBT）。
 * <p>分工：服务端配置决定“能使用哪些”（允许的模式 / 最高档位 / 修复数值），
 * 经验棒界面上的按钮决定“实际使用”（开关 / 模式 / 档位），按物品保存并随菜单 QuickData 同步。
 */
public final class XpRepairSettings {

    /** 经验棒界面开关按钮状态（BD StatusButton 的 Enum 状态）。 */
    public enum ToggleState { ON, OFF }

    private static final String TAG_ENABLED = "beyond_integration:xp_repair_enabled";
    private static final String TAG_MODE = "beyond_integration:xp_repair_mode";
    private static final String TAG_TIER = "beyond_integration:xp_repair_tier";
    private static final String TAG_RANGE_ARMOR = "beyond_integration:xp_repair_range_armor";
    private static final String TAG_RANGE_INVENTORY = "beyond_integration:xp_repair_range_inventory";
    private static final String TAG_RANGE_HANDS = "beyond_integration:xp_repair_range_hands";
    private static final String TAG_RANGE_CURIOS = "beyond_integration:xp_repair_range_curios";
    private static final String TAG_RANGE_MAID = "beyond_integration:xp_repair_range_maid";

    private XpRepairSettings() {}

    // ── 开关（默认开启：物品上无 NBT 时视为开）──

    public static boolean isEnabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getOrCreateTag();
        return !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
    }

    public static void setEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || stack.isEmpty()) return;
        stack.getOrCreateTag().putBoolean(TAG_ENABLED, enabled);
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
        CompoundTag tag = stack.getOrCreateTag();
        return !tag.contains(key) || tag.getBoolean(key);
    }

    private static void setRange(ItemStack stack, String key, boolean v) {
        if (stack == null || stack.isEmpty()) return;
        stack.getOrCreateTag().putBoolean(key, v);
    }

    // ── 模式（百分比 / 固定值）──

    public static CommandConfig.XpRepairAmountMode getMode(ItemStack stack) {
        List<CommandConfig.XpRepairAmountMode> modes = availableModes();
        if (modes.isEmpty()) return CommandConfig.XpRepairAmountMode.PERCENT;
        CommandConfig.XpRepairAmountMode fallback = modes.get(0);
        if (stack == null || stack.isEmpty()) return fallback;
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_MODE)) return fallback;
        try {
            CommandConfig.XpRepairAmountMode mode =
                    CommandConfig.XpRepairAmountMode.valueOf(tag.getString(TAG_MODE));
            return modes.contains(mode) ? mode : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static void setMode(ItemStack stack, CommandConfig.XpRepairAmountMode mode) {
        if (stack == null || stack.isEmpty() || mode == null) return;
        stack.getOrCreateTag().putString(TAG_MODE, mode.name());
    }

    // ── 档位（LOW / NORMAL / HIGH / MAX）──

    public static CommandConfig.XpRepairTier getTier(ItemStack stack) {
        CommandConfig.XpRepairTier max = maxTier();
        CommandConfig.XpRepairTier fallback = clampTier(CommandConfig.XpRepairTier.NORMAL);
        if (stack == null || stack.isEmpty()) return fallback;
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_TIER)) return fallback;
        try {
            return clampTier(CommandConfig.XpRepairTier.valueOf(tag.getString(TAG_TIER)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static void setTier(ItemStack stack, CommandConfig.XpRepairTier tier) {
        if (stack == null || stack.isEmpty() || tier == null) return;
        stack.getOrCreateTag().putString(TAG_TIER, clampTier(tier).name());
    }

    // ── 配置允许集合 ──

    /** 配置允许的修复模式（至少一种；两种都关时返回空）。 */
    public static List<CommandConfig.XpRepairAmountMode> availableModes() {
        List<CommandConfig.XpRepairAmountMode> modes = new ArrayList<>(2);
        if (CommandConfig.xpRepairAllowPercent()) modes.add(CommandConfig.XpRepairAmountMode.PERCENT);
        if (CommandConfig.xpRepairAllowAbsolute()) modes.add(CommandConfig.XpRepairAmountMode.ABSOLUTE);
        return modes;
    }

    /** 配置允许的最高修复档位。 */
    public static CommandConfig.XpRepairTier maxTier() {
        return CommandConfig.xpRepairMaxTier();
    }

    /** 将档位收敛到配置允许范围（不超过 maxTier）。 */
    public static CommandConfig.XpRepairTier clampTier(CommandConfig.XpRepairTier tier) {
        if (tier == null) return CommandConfig.XpRepairTier.LOW;
        int max = maxTier().ordinal();
        return tier.ordinal() <= max ? tier : CommandConfig.XpRepairTier.values()[max];
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
