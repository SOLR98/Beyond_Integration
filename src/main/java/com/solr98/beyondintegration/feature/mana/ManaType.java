package com.solr98.beyondintegration.feature.mana;

import com.wintercogs.beyonddimensions.api.longtype.LongType;
import net.minecraft.network.chat.Component;

/**
 * 网络"魔力"数值堆叠类型（纯数值 {@link LongType}）。
 * <p>用于 Iron's Spells 'n Spellbooks 联动的额外魔力源账户（与机器 FE 分离）。
 */
public final class ManaType extends LongType<ManaType> {

    public ManaType(long amount) {
        this.stackCount = amount;
    }

    @Override
    public Component getName() {
        return Component.translatable("types.beyond_integration.mana_type.name");
    }

    @Override
    public ManaType getEmpty() {
        return new ManaType(0);
    }

    @Override
    public ManaType copy() {
        return new ManaType(stackCount);
    }

    @Override
    public ManaType copyWithAmount(long amount) {
        return new ManaType(amount);
    }
}
