package com.solr98.beyondintegration.handler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleNetStorage {
    private static final Map<UUID, Integer> map = new ConcurrentHashMap<>();
    public static void bindVehicle(UUID vehicleUuid, int netId) { map.put(vehicleUuid, netId); }
    public static void unbindVehicle(UUID vehicleUuid) { map.remove(vehicleUuid); }
    public static int getBoundNetId(UUID vehicleUuid) { return map.getOrDefault(vehicleUuid, -1); }
    public static DimensionsNet getNetworkForVehicle(UUID vehicleUuid) {
        Integer netId = map.get(vehicleUuid);
        return netId == null ? null : DimensionsNet.getNetFromId(netId);
    }
}
