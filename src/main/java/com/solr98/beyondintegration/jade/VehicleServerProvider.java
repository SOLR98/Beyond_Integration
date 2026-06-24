package com.solr98.beyondintegration.jade;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum VehicleServerProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String NET_ID_KEY = "Net_id";
    private static final String NET_NAME_KEY = "bce_net_name";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        int netId = VehicleNetStorage.getBoundNetId(accessor.getEntity().getUUID());
        if (netId < 0) return;
        data.putInt(NET_ID_KEY, netId);
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net instanceof NetworkNameProvider nnp) {
            String name = nnp.getCustomName();
            if (name != null && !name.isEmpty()) data.putString(NET_NAME_KEY, name);
        }
    }

    @Override
    public ResourceLocation getUid() { return ResourceLocation.parse("beyond_integration:vehicle_server"); }
}
