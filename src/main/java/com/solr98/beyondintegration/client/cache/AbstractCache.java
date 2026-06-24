package com.solr98.beyondintegration.client.cache;

import java.util.function.Supplier;

/**
 * Base class for client-side caches with TTL and dedup request tracking.
 *
 * @param <K> the request key type
 * @param <V> the cached value type
 */
public abstract class AbstractCache<K, V> {

    private final long ttlMillis;
    private volatile long lastFetchTime;
    private volatile V cachedData;
    private volatile K pendingRequest;
    private volatile long pendingSince;

    protected AbstractCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
        this.lastFetchTime = 0;
    }

    /** Check whether cached data is still valid (non-null and within TTL). */
    public boolean isValid() {
        return cachedData != null && (System.currentTimeMillis() - lastFetchTime) < ttlMillis;
    }

    /** Get the cached value (may be null). */
    public V get() {
        return cachedData;
    }

    /** Update the cache with fresh data. */
    public void put(V data) {
        this.cachedData = data;
        this.lastFetchTime = System.currentTimeMillis();
        this.pendingRequest = null;
    }

    /** Invalidate the cache, forcing a refresh on next access. */
    public void invalidate() {
        this.cachedData = null;
        this.lastFetchTime = 0;
        this.pendingRequest = null;
    }

    /**
     * Check whether a request for {@code key} is already pending.
     * If not, mark it as pending and return false (caller should proceed with fetch).
     * If already pending, return true (caller should skip).
     */
    public boolean isRequestPending(K key) {
        if (pendingRequest != null && pendingRequest.equals(key)
                && (System.currentTimeMillis() - pendingSince) < ttlMillis) {
            return true;
        }
        pendingRequest = key;
        pendingSince = System.currentTimeMillis();
        return false;
    }

    /**
     * Get cached data if valid, otherwise compute via supplier, cache it, and return.
     */
    public V getOrFetch(K key, Supplier<V> fetcher) {
        if (isValid()) {
            return cachedData;
        }
        if (isRequestPending(key)) {
            return cachedData;
        }
        V data = fetcher.get();
        if (data != null) {
            put(data);
        }
        return data;
    }

    protected long getTtlMillis() {
        return ttlMillis;
    }
}
