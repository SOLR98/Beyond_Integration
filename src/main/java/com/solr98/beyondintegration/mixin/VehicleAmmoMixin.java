package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleAmmoMixin {

    @Inject(method = "getAmmo(Lcom/atsuishio/superbwarfare/data/gun/GunData;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetAmmo(GunData data, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() >= Integer.MAX_VALUE / 2) return;
        if (cir.getReturnValue() > 0) return;

        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;

        DimensionsNet net = VehicleNetStorage.getNetworkForVehicle(vehicle.getUUID());
        if (net == null) return;

        AmmoConsumer consumer = data.selectedAmmoConsumer();
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            Ammo ammoType = consumer.getPlayerAmmoType();
            if (ammoType == null) return;
            if (!(net instanceof SuperbAmmoAccessor acc)) return;
            var map = acc.getSuperbAmmo();
            if (map.getOrDefault("__infinite__", 0L) > 0) {
                cir.setReturnValue(Integer.MAX_VALUE);
                return;
            }
            long n = map.getOrDefault(ammoType.serializationName, 0L);
            if (n > 0) {
                cir.setReturnValue((int) Math.min(n, Integer.MAX_VALUE));
            }
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
            ItemStack ammoStack = consumer.stack();
            if (ammoStack.isEmpty()) return;
            if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
                cir.setReturnValue(Integer.MAX_VALUE);
                return;
            }
            int networkCount = countItemsInNetwork(net, ammoStack);
            if (networkCount > 0) {
                cir.setReturnValue(Math.min(networkCount, Integer.MAX_VALUE - 1));
            }
        }
    }

    private int countItemsInNetwork(DimensionsNet net, ItemStack target) {
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return 0;
        var bucket = opt.get();
        ItemStackKey targetKey = new ItemStackKey(target);
        long total = 0;
        for (int i = 0; i < bucket.size(); i++) {
            var rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            if (!ik.isSame(targetKey)) continue;
            total += net.getUnifiedStorage().getStackByKey(ik).amount();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }
}
