package com.solr98.beyondintegration.jade;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade 服务端数据提供器：为 Superb Warfare 载具实体（VehicleEntity）补充网络信息。
 * 读取载具缓存的 BD 维度网络，将网络 ID 与自定义名称写入服务端数据供客户端显示。
 */
public enum VehicleServerProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    /** 本提供器的唯一标识。 */
    private static final ResourceLocation UID = new ResourceLocation("beyond_integration", "vehicle_network");

    /** 将载具绑定的 BD 网络 ID 与名称写入服务端数据标签。 */
    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof VehicleEntity vehicle)) return;
        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();
        if (net == null) return;

        tag.putInt("beyond_vehicle_net", net.getId());
        if (net instanceof NetworkNameProvider nnp) {
            String name = nnp.getCustomName();
            if (name != null && !name.isEmpty()) tag.putString("beyond_net_name", name);
        }
    }

    /** 返回本提供器的 UID，用于与客户端提供器配对。 */
    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
