package com.solr98.beyondintegration.handler;

/** Mixin-injected interface on {@code FakePlayer} for binding it to a BD network. */
public interface IFakePlayerNetId {
    int beyond$getNetId();
    void beyond$setNetId(int netId);
}
