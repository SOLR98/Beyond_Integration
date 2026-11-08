package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.feature.ammo.sw.SwAmmoStorage;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入超级战争(Superb Warfare)的 VehicleEntity：
 * 扩展 getAmmo——当载具弹药/玩家背包弹药不足时，从绑定的维度网络读取
 * 对应弹药物品（散装弹药）的库存量，创造弹药箱在网时按无限处理。
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleAmmoMixin {

    /** 原返回无弹药时，从维度网络库存补充弹药数量（仅服务端） */
    @Inject(method = "getAmmo(Lcom/atsuishio/superbwarfare/data/gun/GunData;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetAmmo(GunData data, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() >= Integer.MAX_VALUE / 2) return;
        if (cir.getReturnValue() > 0) return;

        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;

        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();
        if (net == null) return;

        AmmoConsumer consumer = data.selectedAmmoConsumer();
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            Ammo ammoType = consumer.getPlayerAmmoType();
            if (ammoType == null) return;
            if (SwAmmoStorage.hasInfinite(net)) {
                cir.setReturnValue(Integer.MAX_VALUE);
                return;
            }
            long n = SwAmmoStorage.count(net, ammoType);
            if (n > 0) {
                cir.setReturnValue((int) Math.min(n, Integer.MAX_VALUE));
            }
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
            ItemStack ammoStack = consumer.stack();
            if (ammoStack.isEmpty()) return;
            if (SwAmmoStorage.hasInfinite(net)) {
                cir.setReturnValue(Integer.MAX_VALUE);
                return;
            }
            long networkCount = net.getUnifiedStorage()
                    .getStackByKey(new ItemStackKey(ammoStack)).amount();
            if (networkCount > 0) {
                cir.setReturnValue((int) Math.min(networkCount, Integer.MAX_VALUE - 1));
            }
        }
    }
}
