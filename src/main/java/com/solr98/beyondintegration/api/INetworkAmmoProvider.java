package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

public interface INetworkAmmoProvider {

    String getAmmoTypeKey();

    long getAmmoCount(DimensionsNet net);

    boolean hasInfiniteAmmo(DimensionsNet net);

    long consumeAmmo(DimensionsNet net, long amount);
}
