package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
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

        int boundNetId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (boundNetId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(boundNetId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(vehicle.getUUID());
            return;
        }

        double pct = CommandConfig.vehicleChargePercentage();
        long want;
        if (pct > 0) {
            want = (long) Math.ceil(needed * pct / 100.0);
        } else {
            want = Math.min(needed, CommandConfig.SERVER.swVehicleEnergyChargeRate.get());
        }
        if (want <= 0) return;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, want, false, false).amount();
        if (got <= 0) return;

        int transfer = (int) Math.min(got, Integer.MAX_VALUE);
        IEnergyStorage energyStorage = vehicle.getEnergyStorage();
        if (energyStorage != null && energyStorage.canReceive()) {
            energyStorage.receiveEnergy(transfer, false);
            net.setDirty();
        }
    }
}
