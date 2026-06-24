package com.solr98.beyondintegration.handler;

/** Mixin-injected interface on {@code BDBaseMenu} subclasses for reading/writing the bound network ID. */
public interface NetIdAccessor {
    int beyond$getNetId();
    void beyond$setNetId(int netId);
}
