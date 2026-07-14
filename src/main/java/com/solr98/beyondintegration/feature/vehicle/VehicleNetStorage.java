package com.solr98.beyondintegration.feature.vehicle;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindData;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleNetStorage {

    private static final Map<UUID, BindData> map = new ConcurrentHashMap<>();

    public static void bindVehicle(UUID vehicleUuid, int netId, UUID playerUuid) {
        if (netId >= 0) {
            UUID token = BindingTokenManager.getOrCreateToken(netId, playerUuid);
            map.put(vehicleUuid, new BindData(netId, token, playerUuid));
        }
    }

    public static void bindVehicleWithToken(UUID vehicleUuid, int netId, UUID token, UUID playerUuid) {
        if (netId >= 0) {
            map.put(vehicleUuid, new BindData(netId, token, playerUuid));
        }
    }

    public static void unbindVehicle(UUID vehicleUuid) {
        BindData old = map.remove(vehicleUuid);
        if (old != null && CommandConfig.enableAuditLog()) {
            NetworkBindingRegistry.removeVehicleBind(old.netId(), vehicleUuid);
        }
    }

    public static BindData getBindData(UUID vehicleUuid) {
        return map.get(vehicleUuid);
    }

    public static DimensionsNet getNetworkForVehicle(UUID vehicleUuid) {
        BindData data = map.get(vehicleUuid);
        if (data == null) return null;
        DimensionsNet net = DimensionsNet.getNetFromId(data.netId());
        if (net == null) {
            map.remove(vehicleUuid);
            return null;
        }
        if (CommandConfig.enableTokenSystem() && !BindingTokenManager.isTokenValid(data.netId(), data.token())) {
            map.remove(vehicleUuid);
            return null;
        }
        return net;
    }

    public static void cleanupStale() {
        Iterator<Map.Entry<UUID, BindData>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BindData> e = it.next();
            if (DimensionsNet.getNetFromId(e.getValue().netId()) == null) {
                it.remove();
            } else if (CommandConfig.enableTokenSystem() && !BindingTokenManager.isTokenValid(e.getValue().netId(), e.getValue().token())) {
                it.remove();
            }
        }
    }

    public static int size() {
        return map.size();
    }
}
