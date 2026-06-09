package com.solr98.beyondintegration.jade;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum VehicleClientProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.tryParse("beyond_integration:vehicle_network");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof VehicleEntity)) return;
        CompoundTag data = accessor.getServerData();
        if (!data.contains("beyond_vehicle_net")) return;
        int netId = data.getInt("beyond_vehicle_net");
        if (netId < 0) return;

        String name = data.contains("beyond_net_name") ? data.getString("beyond_net_name") : "";
        Component text;
        if (!name.isEmpty()) {
            text = Component.literal("§b" + name + " §7(Net#" + netId + "§7)");
        } else {
            text = Component.literal("Net#" + netId);
        }
        tooltip.add(text);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
