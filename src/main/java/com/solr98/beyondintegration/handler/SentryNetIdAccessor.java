package com.solr98.beyondintegration.handler;

import java.util.UUID;

public interface SentryNetIdAccessor {
    int getSentryNetId();
    void setSentryNetId(int netId);
    UUID getSentryToken();
    void setSentryToken(UUID token);
    UUID getSentryOwner();
    void setSentryOwner(UUID uuid);

    default void clearSentryBinding() {
        setSentryNetId(-1);
        setSentryToken(null);
        setSentryOwner(null);
    }
}
