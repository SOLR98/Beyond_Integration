package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.machine.MachineItem;
import com.wintercogs.beyonddimensions.api.machine.MachineSettings;
import com.wintercogs.beyonddimensions.common.init.BDMachineSettings;
import com.wintercogs.beyonddimensions.common.machine.setting.FeederMode;
import com.wintercogs.beyonddimensions.common.machine.setting.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.setting.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.setting.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.setting.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.setting.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.setting.HopperXpMode;
import com.wintercogs.beyonddimensions.common.machine.setting.RedStoneControlMode;
import net.minecraft.world.item.ItemStack;

/**
 * BD 0.8 机器设置读取助手：替代旧版 {@code BaseMachineItem.getXxxModeOrDefault(stack, default)}。
 * <p>
 * 新体系中设置统一存在物品 NBT 的 {@code machine_settings} 下，默认值由 {@code MachineSetting} 自己定义。
 */
public final class MachineSettingsHelper
{
    private MachineSettingsHelper()
    {
    }

    public static MachineSettings of(ItemStack stack)
    {
        if (stack.getItem() instanceof MachineItem machineItem)
        {
            return machineItem.settings(stack);
        }
        return MachineSettings.EMPTY;
    }

    public static FilterMode filterMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.FILTER.get());
    }

    public static HopperItemMode hopperItemMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.COLLECT_ITEMS.get());
    }

    public static HopperXpMode hopperXpMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.COLLECT_EXPERIENCE.get());
    }

    public static HopperFluidMode hopperFluidMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.COLLECT_FLUIDS.get());
    }

    public static HopperNBTMode hopperNBTMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.COLLECT_COMPONENTS.get());
    }

    public static HopperRangeMode hopperRangeMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.RANGE.get());
    }

    public static FeederMode feederMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.FEEDING.get());
    }

    public static RedStoneControlMode redStoneControlMode(ItemStack stack)
    {
        return of(stack).get(BDMachineSettings.REDSTONE.get());
    }
}
