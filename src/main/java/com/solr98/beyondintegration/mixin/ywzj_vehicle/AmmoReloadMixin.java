package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon;

@Mixin(AbstractVehicleWeapon.class)
public class AmmoReloadMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "hasStorageAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$hasNetworkAmmo(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;

        AbstractVehicleWeapon<?> self = (AbstractVehicleWeapon<?>) (Object) this;
        if (self.getVehicle().level().isClientSide()) return;
        if (self.getRemainAmmo() >= self.getMaxCapacity()) return;

        AbstractVehicle vehicle = self.getVehicle();
        int netId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (netId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return;

        Ingredient ammoType = self.getData().getReload().getAmmo();
        if (ammoType == null) return;

        for (ItemStack stack : ammoType.getItems()) {
            if (net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount() > 0) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void beyond$fillRemainingFromNetwork(CallbackInfo ci) {
        AbstractVehicleWeapon<?> self = (AbstractVehicleWeapon<?>) (Object) this;
        if (self.getVehicle().level().isClientSide()) return;

        AbstractVehicle vehicle = self.getVehicle();
        int netId = VehicleNetStorage.getBoundNetId(vehicle.getUUID());
        if (netId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return;

        Ingredient ammoType = self.getData().getReload().getAmmo();
        if (ammoType == null) return;

        int needed = self.getMaxCapacity() - self.getRemainAmmo();
        if (needed <= 0) return;

        int total = 0;
        for (ItemStack stack : ammoType.getItems()) {
            if (total >= needed) break;
            var key = new ItemStackKey(stack);
            long available = net.getUnifiedStorage().getStackByKey(key).amount();
            if (available <= 0) continue;
            long extract = Math.min(available, needed - total);
            net.getUnifiedStorage().extract(key, extract, false, false);
            total += (int) extract;
        }

        if (total > 0) {
            self.setRemainAmmo(self.getRemainAmmo() + total);
            net.setDirty();
            LOGGER.info("[BD-Net] Filled {} ammo from network after reload", total);
        }
    }
}
