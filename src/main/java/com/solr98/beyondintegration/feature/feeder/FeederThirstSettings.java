package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 网络喂食器口渴相关设置的 NBT 读写（存放在喂食器物件的 ItemStack 上，
 * 键名统一加 {@code bi_} 前缀，避免与 BD 自身字段冲突）。
 */
public final class FeederThirstSettings
{
    /** 补水档位键 */
    public static final String KEY_MODE = "bi_thirst_mode";
    /** 回血模式开关键 */
    public static final String KEY_REGEN = "bi_regen_mode";

    private FeederThirstSettings() {}

    public static FeederThirstMode getThirstMode(ItemStack stack)
    {
        if (stack != null && stack.hasTag() && stack.getTag().contains(KEY_MODE))
        {
            String name = stack.getTag().getString(KEY_MODE);
            try
            {
                return FeederThirstMode.valueOf(name);
            }
            catch (IllegalArgumentException ignored)
            {
                // 未知/旧值（如 OFF）一律回落到默认档
            }
        }
        return FeederThirstMode.NORMAL;
    }

    public static void setThirstMode(ItemStack stack, @Nullable FeederThirstMode mode)
    {
        if (stack == null) return;
        stack.getOrCreateTag().putString(KEY_MODE, (mode == null ? FeederThirstMode.NORMAL : mode).name());
    }

    public static boolean isRegenMode(ItemStack stack)
    {
        return stack != null && stack.hasTag() && stack.getTag().getBoolean(KEY_REGEN);
    }

    public static void setRegenMode(ItemStack stack, boolean value)
    {
        if (stack == null) return;
        stack.getOrCreateTag().putBoolean(KEY_REGEN, value);
    }

    /** 补水档位恒有值（无 OFF），始终启用补水逻辑 */
    public static boolean isActive(ItemStack stack)
    {
        return true;
    }
}
