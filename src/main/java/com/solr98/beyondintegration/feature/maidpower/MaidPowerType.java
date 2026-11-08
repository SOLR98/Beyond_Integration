package com.solr98.beyondintegration.feature.maidpower;

import com.wintercogs.beyonddimensions.api.longtype.LongType;
import net.minecraft.network.chat.Component;

/**
 * 网络内「女仆 P 点」的数值堆叠类型（纯数值 {@link LongType}）。
 * <p>单位与 TLM 的 {@code EntityPowerPoint#getValue()} 一致：100 = 1.0 点，避免浮点精度损失。
 */
public final class MaidPowerType extends LongType<MaidPowerType> {

    public MaidPowerType(long amount) {
        this.stackCount = amount;
    }

    @Override
    public Component getName() {
        return Component.translatable("types.beyond_integration.maid_power_type.name");
    }

    @Override
    public MaidPowerType getEmpty() {
        return new MaidPowerType(0);
    }

    @Override
    public MaidPowerType copy() {
        return new MaidPowerType(stackCount);
    }

    @Override
    public MaidPowerType copyWithAmount(long amount) {
        return new MaidPowerType(amount);
    }
}
