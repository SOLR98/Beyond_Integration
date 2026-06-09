package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleNetworkSyncMixin {

    @Unique
    private int beyond$lastBoundNetId = -1;

    @Unique
    private Map<String, Long> beyond$lastAmmo = new HashMap<>();

    @Unique
    private long beyond$lastEnergy = -1;

    @Inject(method = "updateBackupAmmoCount", at = @At("HEAD"))
    private void beyond$syncNetworkStatus(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;

        int boundNetId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());

        if (boundNetId < 0) {
            if (beyond$lastBoundNetId >= 0) {
                beyond$lastBoundNetId = -1;
                beyond$lastAmmo.clear();
                beyond$lastEnergy = -1;
                sendResetToPassengers(vehicle);
            }
            return;
        }

        DimensionsNet net = DimensionsNet.getNetFromId(boundNetId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            if (beyond$lastBoundNetId >= 0) {
                beyond$lastBoundNetId = -1;
                beyond$lastAmmo.clear();
                beyond$lastEnergy = -1;
                sendResetToPassengers(vehicle);
            }
            return;
        }

        Map<String, Long> currentAmmo = buildAmmoMap(net);
        long currentEnergy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();

        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            GunData data = vehicle.getGunData(seat);
            if (data == null) continue;
            AmmoConsumer consumer = data.selectedAmmoConsumer();
            if (consumer == null || consumer.getType() != AmmoConsumer.AmmoConsumeType.ITEM) continue;
            String raw = consumer.stack().isEmpty() ? null
                    : ForgeRegistries.ITEMS.getKey(consumer.stack().getItem()).toString();
            if (raw == null || raw.isEmpty()) {
                raw = consumer.getAmmo();
            }
            if (raw == null || raw.isEmpty()) continue;
            raw = raw.strip();
            int space = raw.indexOf(' ');
            if (space > 0) raw = raw.substring(space + 1).strip();
            if (raw.startsWith("@") || raw.startsWith("#")) raw = raw.substring(1);
            String itemKey = "ITEM:" + raw;

            long itemCount = 0;
            try {
                var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(raw));
                if (item != null) {
                    itemCount = net.getUnifiedStorage().getStackByKey(
                            new ItemStackKey(new ItemStack(item))).amount();
                }
            } catch (Exception ignored) {}
            if (itemCount > 0) {
                currentAmmo.put(itemKey, itemCount);
            }
        }

        if (boundNetId == beyond$lastBoundNetId
                && currentEnergy == beyond$lastEnergy
                && currentAmmo.equals(beyond$lastAmmo)) return;

        beyond$lastBoundNetId = boundNetId;
        beyond$lastEnergy = currentEnergy;
        beyond$lastAmmo = currentAmmo;

        var packet = SuperbAmmoStatusResponsePacket.fromNet(
                net, new HashMap<>(currentAmmo), currentEnergy, 1,
                ((NetworkNameProvider) net).getCustomName());
        for (Entity p : vehicle.getPassengers()) {
            if (p instanceof ServerPlayer sp) {
                PacketHandler.sendToPlayer(sp, packet);
            }
        }
    }

    private static void sendResetToPassengers(VehicleEntity vehicle) {
        var reset = new SuperbAmmoStatusResponsePacket(-1, new HashMap<>(), -1, 1);
        for (Entity p : vehicle.getPassengers()) {
            if (p instanceof ServerPlayer sp) PacketHandler.sendToPlayer(sp, reset);
        }
    }

    private static Map<String, Long> buildAmmoMap(DimensionsNet net) {
        if (net instanceof SuperbAmmoAccessor acc) {
            Map<String, Long> map = new HashMap<>(acc.getSuperbAmmo());
            if (hasInfiniteAmmo(net)) map.put("__infinite__", Long.MAX_VALUE);
            return map;
        }
        return new HashMap<>();
    }

    private static boolean hasInfiniteAmmo(DimensionsNet net) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return false;
        return acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0;
    }
}
