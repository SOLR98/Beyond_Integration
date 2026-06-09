package com.solr98.beyondintegration.cache;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestAmmoCountPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AmmoCountCache {

    private static final Map<String, Map<Integer, Integer>> cache = new ConcurrentHashMap<>();
    private static final Set<String> responded = ConcurrentHashMap.newKeySet();
    private static volatile String pendingQuickId = null;

    public static boolean hasData(ResourceLocation ammoId) {
        return responded.contains(ammoId.toString());
    }

    public static int getCount(ResourceLocation ammoId) {
        Map<Integer, Integer> nets = cache.get(ammoId.toString());
        if (nets == null) return 0;
        return nets.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static Map<Integer, Integer> getAllNetworks(ResourceLocation ammoId) {
        return cache.getOrDefault(ammoId.toString(), Collections.emptyMap());
    }

    public static void requestQuick(ResourceLocation ammoId) {
        String id = ammoId.toString();
        if (id.equals(pendingQuickId)) return;
        pendingQuickId = id;
        responded.remove(id);
        PacketHandler.sendToServer(new RequestAmmoCountPacket(ammoId, true));
    }

    public static void update(ResourceLocation ammoId, Map<Integer, Integer> networkCounts, boolean quick) {
        cache.put(ammoId.toString(), new LinkedHashMap<>(networkCounts));
        if (quick) {
            pendingQuickId = null;
        }
        responded.add(ammoId.toString());
    }

    public static void clear() {
        cache.clear();
        responded.clear();
        pendingQuickId = null;
    }
}
