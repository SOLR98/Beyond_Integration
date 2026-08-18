package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;

/**
 * 由 VehicleNetMixin 实现：载具实体自维护的网络缓存访问器。
 */
public interface INetCachedVehicle {
    VehicleNetCache getNetCache();
}
