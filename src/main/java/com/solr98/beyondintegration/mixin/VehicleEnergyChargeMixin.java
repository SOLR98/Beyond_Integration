package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入超级战争的 VehicleEntity：
 * 扩展 baseTick——按配置的间隔/模式（百分比或固定速率）从绑定维度网络的
 * 能量库存(EnergyStackKey)抽取 FE 充入载具能量槽，实现载具"网络供能"。
 */
@Mixin(value = com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.class, remap = false)
public abstract class VehicleEnergyChargeMixin {

    /** 每 tick 检查：服务端、有能量槽、到达间隔且缺电时，从网络抽取能量并充入 */
    @Inject(method = "baseTick", at = @At("HEAD"), remap = true)
    private void beyond$chargeFromNetwork(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;
        if (!vehicle.hasEnergyStorage()) return;

        int interval = CommandConfig.vehicleChargeInterval();
        if (vehicle.tickCount % interval != 0) return;

        int needed = vehicle.getMaxEnergy() - vehicle.getEnergy();
        if (needed <= 0) return;

        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();
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
