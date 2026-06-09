package com.solr98.beyondintegration.jade;

import com.solr98.beyondintegration.handler.VehicleNetStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum ContainerServerProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String NET_ID_KEY = "Net_id";
    private static final String NET_NAME_KEY = "bce_net_name";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        try {
            Class<?> containerClass = Class.forName("com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity");
            if (!containerClass.isInstance(accessor.getBlockEntity())) return;

            Object blockEntity = accessor.getBlockEntity();
            java.lang.reflect.Field entityTagField = containerClass.getDeclaredField("entityTag");
            entityTagField.setAccessible(true);
            CompoundTag entityTag = (CompoundTag) entityTagField.get(blockEntity);
            if (entityTag == null || !entityTag.contains("uuid")) return;

            String uuidStr = entityTag.getString("uuid");
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            int netId = VehicleNetStorage.getBoundNetId(uuid);
            if (netId < 0) return;

            data.putInt(NET_ID_KEY, netId);
            if (entityTag.contains("customName")) {
                data.putString(NET_NAME_KEY, entityTag.getString("customName"));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:container_server");
    }
}
