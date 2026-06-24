package com.solr98.beyondintegration.handler;

/**
 * Mixin-injected interface on {@code DimensionsNet} for YWZJ creative (infinite) ammo mode.
 * When a YWZJ {@code ammo_creative} item is inserted into the network,
 * all bound YWZJ vehicles using network ammo effectively have infinite ammo
 * (reload fills to max without consuming items from network storage).
 */
public interface YwzjCreativeAccessor {
    boolean beyond$isYwzjCreativeAmmo();
    void beyond$setYwzjCreativeAmmo(boolean creative);
}
