package com.solr98.beyondintegration.handler;

/** Mixin-injected interface on {@code DimensionsNet} for custom network display names. */
public interface NetworkNameProvider {
    /** Returns the custom name of this network, or empty string if unnamed. */
    default String getCustomName() { return ""; }
}
