package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.CommandConfig.ChargeMode;
import com.solr98.beyondintegration.CommandConfig.FuelSource;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ywzj.vehicle.all.AllConfigs;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

@Mixin(AbstractVehicle.class)
public class VehicleEnergyChargeMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "tick", at = @At("HEAD"))
    private void beyond$chargeFromNetwork(CallbackInfo ci) {
        AbstractVehicle self = (AbstractVehicle) (Object) this;
        if (self.level().isClientSide()) return;

        ChargeMode mode = CommandConfig.ywzjChargeMode();
        if (mode == ChargeMode.OFF) return;

        int interval = CommandConfig.ywzjVehicleChargeInterval();
        if (self.tickCount % interval != 0) return;

        int boundNetId = VehicleNetStorage.getBoundNetId(self.getUUID());
        if (boundNetId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(boundNetId);
        if (net == null) {
            VehicleNetStorage.unbindVehicle(self.getUUID());
            return;
        }

        float space = self.energyInfo.energyCapacity - self.getEnergy();
        if (space <= 0) return;

        if (CommandConfig.ywzjFuelSource() == FuelSource.FE) {
            chargeFromFE(net, self, space);
        } else {
            chargeFromFluid(net, self, space);
        }
    }

    private static void chargeFromFE(DimensionsNet net, AbstractVehicle vehicle, float space) {
        int conversion = CommandConfig.ywzjVehicleEnergyConversion();
        long feNeeded = (long) (space * conversion);
        long want;

        if (CommandConfig.ywzjChargeMode() == ChargeMode.PERCENTAGE) {
            want = (long) Math.ceil(feNeeded * CommandConfig.ywzjVehicleChargePercentage() / 100.0);
        } else {
            want = Math.min(CommandConfig.ywzjVehicleEnergyChargeRate(), feNeeded);
        }
        if (want <= 0) return;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, want, false, false).amount();
        if (got <= 0) return;

        vehicle.addEnergy(got / conversion);
        net.setDirty();
    }

    private static void chargeFromFluid(DimensionsNet net, AbstractVehicle vehicle, float space) {
        var bucketOpt = net.getUnifiedStorage().getBucket(FluidStackKey.ID);
        if (bucketOpt.isEmpty()) return;
        var bucket = bucketOpt.get();

        var whitelist = AllConfigs.common.fuelNameWhiteList.get();

        for (int i = 0; i < bucket.size(); i++) {
            if (space <= 0) break;
            var rawKey = bucket.get(i);
            if (!(rawKey instanceof FluidStackKey fluidKey)) continue;

            Fluid fluid = fluidKey.getSource();
            String fluidId = BuiltInRegistries.FLUID.getKey(fluid).toString();

            boolean allowed = whitelist.stream().anyMatch(fluidId::contains);
            if (!allowed) continue;

            long available = net.getUnifiedStorage().getStackByKey(fluidKey).amount();
            if (available <= 0) continue;

            int mbPerFuelUnit = 1000; // 1000 mb fluid = 1 fuel unit (same as FuelTankItem)
            long neededMb = (long) (space * mbPerFuelUnit);
            long toExtract = Math.min(available, neededMb);
            if (toExtract <= 0) continue;

            long extracted = net.getUnifiedStorage().extract(fluidKey, toExtract, false, false).amount();
            if (extracted <= 0) continue;

            float fuelAdded = extracted / (float) mbPerFuelUnit;
            vehicle.addEnergy(fuelAdded);
            space -= fuelAdded;
            net.setDirty();
            LOGGER.info("[BD-Net] Extracted {} mb of fluid {}, added {} fuel units", extracted, fluidId, fuelAdded);
            return;
        }
    }
}
