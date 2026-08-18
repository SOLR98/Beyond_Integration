package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoDeltaS2CPacket;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * 载具网络状态同步：读 VehicleNetCache（实体级缓存）。
 * 仅在有乘客时刷新（B3）；乘客从无到有时强制全量（上车重建）；
 * 解绑时向乘客推送 reset 包。
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleNetworkSyncMixin {

    /** 上次已同步的网络 ID，用于检测绑定变更 */
    @Unique
    private int beyond$lastBoundNetId = -1;

    /** 上次的乘客状态，用于检测乘客上车（触发全量推送） */
    @Unique
    private boolean beyond$lastHasPassengers = false;

    /** 每 tick 刷新网络弹药/能量状态并推送增量或全量包给乘客；解绑时发送 reset 包 */
    @Inject(method = "updateBackupAmmoCount", at = @At("HEAD"))
    private void beyond$syncNetworkStatus(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;

        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();

        if (net == null) {
            if (beyond$lastBoundNetId >= 0) {
                beyond$lastBoundNetId = -1;
                sendResetToPassengers(vehicle);
            }
            beyond$lastHasPassengers = !vehicle.getPassengers().isEmpty();
            return;
        }

        int boundNetId = net.getId();
        boolean hasPassengers = !vehicle.getPassengers().isEmpty();

        if (boundNetId != beyond$lastBoundNetId || (hasPassengers && !beyond$lastHasPassengers)) {
            // 新绑定或新乘客上车：强制全量
            cache.forceFull();
        }
        beyond$lastBoundNetId = boundNetId;
        beyond$lastHasPassengers = hasPassengers;

        // 无乘客不刷新（B3）
        if (!hasPassengers) return;

        VehicleNetCache.PushData push = cache.refresh();
        if (push == null) return;

        for (Entity p : vehicle.getPassengers()) {
            if (!(p instanceof ServerPlayer sp)) continue;
            if (push.full()) {
                PacketHandler.sendToPlayer(sp, new SuperbAmmoStatusResponsePacket(
                        boundNetId, push.netName(), push.energy(), push.enchantSeparation(),
                        push.ammo(), cache.getAmmoList()));
            } else {
                PacketHandler.sendToPlayer(sp, new SuperbAmmoDeltaS2CPacket(
                        boundNetId, true, false, push.ammo(), push.energy(),
                        push.netName(), push.enchantSeparation()));
            }
        }
    }

    /** 解绑时向所有乘客推送网络重置包（清除客户端缓存状态） */
    @Unique
    private static void sendResetToPassengers(VehicleEntity vehicle) {
        var reset = new SuperbAmmoStatusResponsePacket(-1, "", -1, true,
                new HashMap<>(), null);
        for (Entity p : vehicle.getPassengers()) {
            if (p instanceof ServerPlayer sp) PacketHandler.sendToPlayer(sp, reset);
        }
    }
}
