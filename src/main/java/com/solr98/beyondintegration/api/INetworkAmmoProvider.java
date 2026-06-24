package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

/**
 * Extension point for ammo systems to integrate with BD network storage.
 * <p>
 * Implement this interface to add support for a mod-specific ammo type
 * (e.g. SuperbWarfare's {@code Ammo} enum, TACZ's {@code ResourceLocation}-based ammo).
 * <p>
 * Register implementations via {@link com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler#addHandler}.
 */
public interface INetworkAmmoProvider {

    /** Unique key identifying this ammo type (e.g. "superb", "tacz"). */
    String getAmmoTypeKey();

    /** Total count of this ammo type available in the given network. */
    long getAmmoCount(DimensionsNet net);

    /** Whether the given network has infinite ammo (e.g. creative box). */
    boolean hasInfiniteAmmo(DimensionsNet net);

    /** Consume the given amount of ammo from the network. Returns actual amount consumed. */
    long consumeAmmo(DimensionsNet net, long amount);
}
