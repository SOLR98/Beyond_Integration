package com.solr98.beyondintegration.handler;

import java.util.Map;

public interface SuperbAmmoAccessor {
    Map<String, Long> getSuperbAmmo();
    void setSuperbAmmo(Map<String, Long> map);
}
