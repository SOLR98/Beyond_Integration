package com.solr98.beyondintegration.jade;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum VehicleServerProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("beyond_integration", "vehicle_network");

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof VehicleEntity vehicle)) return;
        DimensionsNet net = VehicleNetStorage.getNetworkForVehicle(vehicle.getUUID());
        if (net == null) return;

        tag.putInt("beyond_vehicle_net", net.getId());
        if (net instanceof NetworkNameProvider nnp) {
            String name = nnp.getCustomName();
            if (name != null && !name.isEmpty()) tag.putString("beyond_net_name", name);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
