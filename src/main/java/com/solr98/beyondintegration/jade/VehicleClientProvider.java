package com.solr98.beyondintegration.jade;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 客户端组件提供器：为 Superb Warfare 载具实体显示网络信息。
 * 与服务端 VehicleServerProvider 配对，将网络 ID（及可选名称）渲染到工具提示中。
 */
public enum VehicleClientProvider implements IEntityComponentProvider {
    INSTANCE;

    /** 本提供器的唯一标识（与服务端 UID 对应）。 */
    private static final ResourceLocation UID = ResourceLocation.tryParse("beyond_integration:vehicle_network");

    /** 从服务端数据读取网络信息并追加到工具提示（显示名称与 Net#ID）。 */
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

    /** 返回本提供器的 UID。 */
    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
