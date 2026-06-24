package com.solr98.beyondintegration.maid;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MaidNetworkCache {
    private static final ConcurrentHashMap<UUID, Integer> cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> ts = new ConcurrentHashMap<>();
    private static final long TTL = 5000;

    public static DimensionsNet get(net.minecraft.world.entity.LivingEntity e) {
        Integer id = cache.get(e.getUUID());
        if (id == null) return null;
        Long t = ts.get(e.getUUID());
        if (t == null || System.currentTimeMillis() - t > TTL) { cache.remove(e.getUUID()); ts.remove(e.getUUID()); return null; }
        DimensionsNet n = DimensionsNet.getNetFromId(id);
        if (n == null || n.deleted) { cache.remove(e.getUUID()); ts.remove(e.getUUID()); return null; }
        return n;
    }
    public static void put(UUID u, int id) { cache.put(u, id); ts.put(u, System.currentTimeMillis()); }
    public static void remove(UUID u) { cache.remove(u); ts.remove(u); }
}
