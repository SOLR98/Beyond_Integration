package com.solr98.beyondintegration.feature.soul;

import com.wintercogs.beyonddimensions.api.longtype.LongType;
import net.minecraft.network.chat.Component;

/**
 * 网络"灵魂能量"的数值堆叠类型（纯数值 {@link LongType}）。
 * <p>用于 BeyondDimensions 网络内独立的灵魂能量账户（与机器 FE 分离）。
 */
public final class SoulEnergyType extends LongType<SoulEnergyType> {

    public SoulEnergyType(long amount) {
        this.stackCount = amount;
    }

    @Override
    public Component getName() {
        return Component.translatable("types.beyond_integration.soul_energy_type.name");
    }

    @Override
    public SoulEnergyType getEmpty() {
        return new SoulEnergyType(0);
    }

    @Override
    public SoulEnergyType copy() {
        return new SoulEnergyType(stackCount);
    }

    @Override
    public SoulEnergyType copyWithAmount(long amount) {
        return new SoulEnergyType(amount);
    }
}
