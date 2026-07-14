package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.class, remap = false)
public abstract class VehicleEnergyChargeMixin {

    @Inject(method = "baseTick", at = @At("HEAD"), remap = true)
    private void beyond$chargeFromNetwork(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;
        if (!vehicle.hasEnergyStorage()) return;

        int interval = CommandConfig.vehicleChargeInterval();
        if (vehicle.tickCount % interval != 0) return;

        int needed = vehicle.getMaxEnergy() - vehicle.getEnergy();
        if (needed <= 0) return;

        DimensionsNet net = VehicleNetStorage.getNetworkForVehicle(vehicle.getUUID());
        if (net == null) return;

        double pct = CommandConfig.vehicleChargePercentage();
        long want;
        switch (CommandConfig.vehicleChargeMode()) {
            case PERCENTAGE:
                want = (long) Math.ceil(needed * pct / 100.0);
                break;
            default:
                want = Math.min(needed, CommandConfig.vehicleChargeRate());
                break;
        }
        if (want <= 0) return;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, want, false, false).amount();
        if (got <= 0) return;

        int transfer = (int) Math.min(got, Integer.MAX_VALUE);
        vehicle.getCapability(ForgeCapabilities.ENERGY).ifPresent(cap -> {
            if (cap.canReceive()) {
                cap.receiveEnergy(transfer, false);
                net.setDirty();
            }
        });
    }
}
