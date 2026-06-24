package com.solr98.beyondintegration.feature.vehicle;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleNetStorage {
    private static final Map<UUID, Integer> map = new ConcurrentHashMap<>();
    public static void bindVehicle(UUID vehicleUuid, int netId) { map.put(vehicleUuid, netId); }
    public static void unbindVehicle(UUID vehicleUuid) { map.remove(vehicleUuid); }
    public static int getBoundNetId(UUID vehicleUuid) {
        Integer id = map.get(vehicleUuid);
        if (id == null) return -1;
        if (DimensionsNet.getNetFromId(id) == null) {
            map.remove(vehicleUuid);
            return -1;
        }
        return id;
    }
    public static DimensionsNet getNetworkForVehicle(UUID vehicleUuid) {
        int netId = getBoundNetId(vehicleUuid);
        return netId >= 0 ? DimensionsNet.getNetFromId(netId) : null;
    }
}
