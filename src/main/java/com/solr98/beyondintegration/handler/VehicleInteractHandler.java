package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.HashMap;

public class VehicleInteractHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean vehicleChecked = false;
    private static Class<?> vehicleClass = null;

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isVehicleEntity(event.getTarget())) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        if (event.getSide().isClient()) return;

        event.setCanceled(true);

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            LOGGER.warn("Vehicle bind failed: net {} not found", netId);
            return;
        }

        VehicleNetStorage.bindVehicle(event.getTarget().getUUID(), netId);
        LOGGER.info("Vehicle {} bound to net {}", event.getTarget().getUUID(), netId);

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.beyond_integration.vehicle_bound", netId));

            if (net instanceof SuperbAmmoAccessor acc) {
                long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
                PacketHandler.sendToPlayer(serverPlayer, SuperbAmmoStatusResponsePacket.fromNet(
                        net, new HashMap<>(acc.getSuperbAmmo()), energy, 1, netName));
            }
        }
    }

    private static boolean isVehicleEntity(Object target) {
        if (!vehicleChecked) {
            vehicleChecked = true;
            try {
                vehicleClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            } catch (Exception ignored) {
            }
        }
        return vehicleClass != null && vehicleClass.isInstance(target);
    }
}
