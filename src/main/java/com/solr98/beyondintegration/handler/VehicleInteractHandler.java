package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public class VehicleInteractHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        boolean isVehicle = false;
        String vehicleType = "unknown";
        try {
            Class<?> swClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            if (swClass.isInstance(event.getTarget())) { isVehicle = true; vehicleType = "SW"; }
        } catch (Exception ignored) {}
        if (!isVehicle) {
            try {
                Class<?> ywzjClass = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
                if (ywzjClass.isInstance(event.getTarget())) { isVehicle = true; vehicleType = "ywzj"; }
            } catch (Exception ignored) {}
        }
        if (!isVehicle) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        LOGGER.info("[BD-Net] {} vehicle interact with network card: entity={} netId={}",
                vehicleType, event.getTarget().getUUID(), netId);

        // 检查网络是否存在
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            LOGGER.warn("[BD-Net] Network {} not found for vehicle binding!", netId);
        } else {
            VehicleNetStorage.bindVehicle(event.getTarget().getUUID(), netId);
            LOGGER.info("[BD-Net] Vehicle {} bound to network #{} (name: {})",
                    event.getTarget().getUUID(), netId,
                    net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "unnamed");
        }

        event.setCanceled(true);
    }
}
