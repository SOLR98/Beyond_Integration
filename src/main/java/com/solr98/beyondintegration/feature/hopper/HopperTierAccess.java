package com.solr98.beyondintegration.feature.hopper;

/**
 * 维度网络漏斗（{@code net_hopper_block}）的 BI 档位访问接口：
 * 每方块独立的物品/流体吸取档位索引（存入方块实体 NBT）。
 */
public interface HopperTierAccess {

    int beyond$getItemTier();

    void beyond$setItemTier(int value);

    int beyond$getFluidTier();

    void beyond$setFluidTier(int value);
}
