package com.solr98.beyondintegration.feature.hopper;

/**
 * 维度网络漏斗（{@code net_hopper_block}）的 BI 档位访问接口：1.21.1。
 */
public interface HopperTierAccess {

    int beyond$getItemTier();

    void beyond$setItemTier(int value);

    int beyond$getFluidTier();

    void beyond$setFluidTier(int value);
}
