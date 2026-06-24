package com.solr98.beyondintegration.jade;

import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.mixin.ContainerBlockEntityAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.UUID;

public enum ContainerServerProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String NET_ID_KEY = "Net_id";
    private static final String NET_NAME_KEY = "bce_net_name";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        var be = accessor.getBlockEntity();
        if (!(be instanceof ContainerBlockEntityAccessor acc)) return;

        CompoundTag entityTag = acc.getEntityTag();
        if (entityTag == null || !entityTag.contains("uuid")) return;

        String uuidStr = entityTag.getString("uuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        int netId = VehicleNetStorage.getBoundNetId(uuid);
        if (netId < 0) return;

        data.putInt(NET_ID_KEY, netId);
        if (entityTag.contains("customName")) {
            data.putString(NET_NAME_KEY, entityTag.getString("customName"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:container_server");
    }
}
