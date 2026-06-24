package com.solr98.beyondintegration.handler;
import java.util.Set;

/** Mixin-injected interface on {@code DimensionsNet} tracking TACZ creative (infinite) ammo types. */
public interface TaczCreativeAccessor {
    /** Returns the set of ammo IDs that have a creative ammo box in this network ("*" = all types). */
    Set<String> getTaczCreativeTypes();
    void setTaczCreativeTypes(Set<String> types);
}
