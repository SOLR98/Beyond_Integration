package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 网络喂食器口渴相关设置的读写（1.21.1 用 {@code CUSTOM_DATA} 组件存放，
 * 键名统一加 {@code bi_} 前缀，避免与其它自定义数据冲突）。
 */
public final class FeederThirstSettings
{
    /** 补水档位键 */
    public static final String KEY_MODE = "bi_thirst_mode";
    /** 回血模式开关键 */
    public static final String KEY_REGEN = "bi_regen_mode";

    private FeederThirstSettings() {}

    private static CompoundTag read(ItemStack stack)
    {
        if (stack == null) return new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void update(ItemStack stack, Consumer<CompoundTag> op)
    {
        if (stack == null) return;
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(op));
    }

    public static FeederThirstMode getThirstMode(ItemStack stack)
    {
        CompoundTag tag = read(stack);
        if (tag.contains(KEY_MODE))
        {
            try
            {
                return FeederThirstMode.valueOf(tag.getString(KEY_MODE));
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
        FeederThirstMode value = mode == null ? FeederThirstMode.NORMAL : mode;
        update(stack, tag -> tag.putString(KEY_MODE, value.name()));
    }

    public static boolean isRegenMode(ItemStack stack)
    {
        return read(stack).getBoolean(KEY_REGEN);
    }

    public static void setRegenMode(ItemStack stack, boolean value)
    {
        update(stack, tag -> tag.putBoolean(KEY_REGEN, value));
    }

    /** 补水档位恒有值（无 OFF），始终启用补水逻辑 */
    public static boolean isActive(ItemStack stack)
    {
        return true;
    }
}
