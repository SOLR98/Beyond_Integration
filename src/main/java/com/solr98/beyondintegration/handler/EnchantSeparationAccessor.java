package com.solr98.beyondintegration.handler;

/** Mixin-injected interface on {@code DimensionsNet} for toggling enchantment separation per-network. */
public interface EnchantSeparationAccessor {
    boolean beyond$isEnchantSeparationEnabled();
    void beyond$setEnchantSeparationEnabled(boolean enabled);
}
