package com.solr98.beyondintegration.maid;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MaidNetworkCache {
    static final Map<UUID, Integer> cachedNetId = new HashMap<>();

    public static DimensionsNet get(LivingEntity entity) {
        Integer netId = cachedNetId.get(entity.getUUID());
        if (netId == null) return null;
        if (CommandConfig.enableTokenSystem()) {
            remove(entity.getUUID());
            return null;
        }
        return DimensionsNet.getNetFromId(netId);
    }

    public static void put(UUID uuid, int netId) {
        cachedNetId.put(uuid, netId);
    }

    public static void remove(UUID uuid) {
        cachedNetId.remove(uuid);
    }
}
