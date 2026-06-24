package com.solr98.beyondintegration.handler;
import java.util.Map;

/** Mixin-injected interface on {@code DimensionsNet} for virtual SuperbWarfare ammo storage. */
public interface SuperbAmmoAccessor {
    /** Returns the map of ammo type name → count stored virtually in this network. */
    Map<String, Long> getSuperbAmmo();
    void setSuperbAmmo(Map<String, Long> map);
}
