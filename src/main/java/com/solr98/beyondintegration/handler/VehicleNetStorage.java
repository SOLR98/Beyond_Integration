package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleNetStorage {

    private static final Map<UUID, Integer> map = new ConcurrentHashMap<>();

    public static void bindVehicle(UUID vehicleUuid, int netId) {
        if (netId >= 0) {
            map.put(vehicleUuid, netId);
        }
    }

    public static void unbindVehicle(UUID vehicleUuid) {
        map.remove(vehicleUuid);
    }

    public static int getBoundNetId(UUID vehicleUuid) {
        return map.getOrDefault(vehicleUuid, -1);
    }

    public static DimensionsNet getNetworkForVehicle(UUID vehicleUuid) {
        Integer netId = map.get(vehicleUuid);
        if (netId == null) return null;
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            map.remove(vehicleUuid);
            return null;
        }
        return net;
    }

    public static void cleanupStale() {
        Iterator<Map.Entry<UUID, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (DimensionsNet.getNetFromId(e.getValue()) == null) {
                it.remove();
            }
        }
    }

    public static int size() {
        return map.size();
    }
}
