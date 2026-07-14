package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LogicalSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityReload.class, remap = false)
public class LivingEntityReloadMixin {

    @Shadow private LivingEntity shooter;
    @Shadow private ShooterDataHolder data;
    @Shadow private LivingEntityDrawGun draw;
    @Shadow private LivingEntityShoot shoot;

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void onReload(CallbackInfo ci) {
        if (data.currentGunItem == null) return;
        ItemStack currentGunItem = data.currentGunItem.get();
        if (!(currentGunItem.getItem() instanceof AbstractGunItem gunItem)) return;
        if (!gunItem.useInventoryAmmo(currentGunItem)) return;

        ResourceLocation gunId = gunItem.getGunId(currentGunItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(gunIndex -> {
            if (data.reloadStateType.isReloading()) return;
            if (shoot.getShootCoolDown() != 0) return;
            if (draw.getDrawCoolDown() != 0) return;
            if (data.isBolting) return;
            if (IGunOperator.fromLivingEntity(shooter).needCheckAmmo() && !gunItem.canReload(shooter, currentGunItem)) return;
            if (MinecraftForge.EVENT_BUS.post(new GunReloadEvent(shooter, currentGunItem, LogicalSide.SERVER))) return;

            NetworkHandler.sendToTrackingEntity(new ServerMessageGunReload(shooter.getId(), currentGunItem), shooter);
            Bolt boltType = gunIndex.getGunData().getBolt();
            int ammoCount = gunItem.getCurrentAmmoCount(currentGunItem) + (gunItem.hasBulletInBarrel(currentGunItem) && boltType != Bolt.OPEN_BOLT ? 1 : 0);
            if (ammoCount <= 0) {
                data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FEEDING;
            } else {
                data.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FEEDING;
            }
            data.reloadTimestamp = System.currentTimeMillis();
            if (!gunItem.startReload(data, currentGunItem, shooter)) {
                data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
                data.reloadTimestamp = -1;
            }
        });
        ci.cancel();
    }
}
