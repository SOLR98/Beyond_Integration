package com.solr98.beyondintegration.core.sync;

import com.solr98.beyondintegration.feature.soul.SoulEnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

/**
 * 可共享的数据类型（用于主网络同步范围与条件式取数）。
 * <p>{@link #bit()} 用作传输包 + 客户端镜像的「可用类型位」；{@link #of(IStackKey)} 把存储键归类。
 * 未知键（非物品/能量/灵魂）不归入任何类型，不参与类型位。
 */
public enum NetDataType {
    ITEM,
    ENERGY,
    SOUL,
    EXT_AMMO,
    EXT_FLAGS;

    public int bit() {
        return 1 << ordinal();
    }

    /** 全部已知类型位掩码。 */
    public static int allBits() {
        int m = 0;
        for (NetDataType t : values()) m |= t.bit();
        return m;
    }

    /** 把存储键归类为可共享类型；未知返回 {@code null}。 */
    public static NetDataType of(IStackKey<?> key) {
        if (key instanceof ItemStackKey) return ITEM;
        if (key instanceof EnergyStackKey) return ENERGY;
        if (key instanceof SoulEnergyStackKey) return SOUL;
        return null;
    }
}
