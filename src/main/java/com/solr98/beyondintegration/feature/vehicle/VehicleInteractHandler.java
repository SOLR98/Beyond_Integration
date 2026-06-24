package com.solr98.beyondintegration.feature.vehicle;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public class VehicleInteractHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Class<?> swVehicleClass;
    private Class<?> ywzjVehicleClass;
    private boolean classCheckDone;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ensureClassesResolved();
        if (!isVehicle(event.getTarget())) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        LOGGER.debug("[BD-Net] vehicle interact with network card: entity={} netId={}",
                event.getTarget().getUUID(), netId);

        // 检查网络是否存在
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            LOGGER.warn("[BD-Net] Network {} not found for vehicle binding!", netId);
        } else {
            VehicleNetStorage.bindVehicle(event.getTarget().getUUID(), netId);
            LOGGER.debug("[BD-Net] Vehicle {} bound to network #{} (name: {})",
                    event.getTarget().getUUID(), netId,
                    net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "unnamed");
        }

        event.setCanceled(true);
    }

    private void ensureClassesResolved() {
        if (classCheckDone) return;
        classCheckDone = true;
        try {
            swVehicleClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
        } catch (Exception e) {
            LOGGER.debug("[BD-Net] SW vehicle class not available: {}", e.getMessage());
        }
        try {
            ywzjVehicleClass = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
        } catch (Exception e) {
            LOGGER.debug("[BD-Net] YWZJ vehicle class not available: {}", e.getMessage());
        }
    }

    private boolean isVehicle(Object target) {
        return (swVehicleClass != null && swVehicleClass.isInstance(target))
                || (ywzjVehicleClass != null && ywzjVehicleClass.isInstance(target));
    }
}
