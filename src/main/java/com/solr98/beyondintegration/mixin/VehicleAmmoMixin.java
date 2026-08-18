package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
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
 * 对应弹药类型（玩家弹药或物品弹药）的库存量，支持网络无限弹药标志。
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

    /** 统计网络中与目标物品同种（ItemStackKey）的库存总量 */
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
