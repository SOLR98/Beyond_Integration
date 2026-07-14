package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestAmmoCountPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaczAmmoCache {

    private static final Map<String, Integer> cache = new ConcurrentHashMap<>();
    private static volatile boolean hasData = false;
    private static volatile boolean requestPending = false;

    public static boolean hasData(ResourceLocation ammoId) {
        return hasData;
    }

    public static int getCount(ResourceLocation ammoId) {
        if (!hasData) return 0;
        Integer allCreative = cache.get("*");
        if (allCreative != null && allCreative == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return cache.getOrDefault(ammoId.toString(), 0);
    }

    public static Map<Integer, Integer> getAllNetworks(ResourceLocation ammoId) {
        if (!hasData) return Collections.emptyMap();
        int count = getCount(ammoId);
        if (count > 0) {
            return Collections.singletonMap(-1, count);
        }
        return Collections.emptyMap();
    }

    public static void requestQuick(ResourceLocation ammoId) {
        if (requestPending) return;
        requestPending = true;
        PacketHandler.sendToServer(new RequestAmmoCountPacket());
    }

    public static void update(Map<String, Integer> ammoMap) {
        cache.clear();
        cache.putAll(ammoMap);
        hasData = true;
        requestPending = false;
    }

    public static void applyPush(String ammoId, int newCount) {
        if (newCount > 0) {
            cache.put(ammoId, newCount);
        } else {
            cache.remove(ammoId);
        }
    }

    public static void clear() {
        cache.clear();
        hasData = false;
        requestPending = false;
    }
}
