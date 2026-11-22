package com.solr98.beyondintegration.modules.sw;

import com.solr98.beyondintegration.modules.sw.VehicleNetCache;
import com.solr98.beyondintegration.mixin.sw.VehicleNetMixin;

/**
 * 由 VehicleNetMixin 实现：载具实体自维护的网络缓存访问器。
 */
public interface INetCachedVehicle {
    /** 获取载具实体缓存的网络缓存对象 */
    VehicleNetCache getNetCache();
}
