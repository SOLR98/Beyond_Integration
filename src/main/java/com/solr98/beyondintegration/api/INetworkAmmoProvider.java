package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

/**
 * 网络弹药提供接口。
 * 由外部模组实现，将自身的弹药系统与维度网络存储对接：
 * 查询弹药数量、是否无限弹药以及从网络消耗弹药。
 */
public interface INetworkAmmoProvider {

    /** 弹药类型键，用于标识该提供器管理的弹药类型。 */
    String getAmmoTypeKey();

    /** 查询网络中当前可用的弹药数量。 */
    long getAmmoCount(DimensionsNet net);

    /** 查询该弹药在网络中是否为无限（不消耗）。 */
    boolean hasInfiniteAmmo(DimensionsNet net);

    /** 从网络中消耗指定数量的弹药，返回实际消耗量。 */
    long consumeAmmo(DimensionsNet net, long amount);
}
