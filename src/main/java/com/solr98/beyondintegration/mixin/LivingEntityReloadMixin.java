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

/**
 * 注入 TACZ 的 {@link LivingEntityReload}（服务端实体换弹逻辑），
 * 在 reload 开头提前执行完整换弹流程（事件广播、同步客户端、设置换弹状态并调用 startReload），
 * 以配合扩展后的 canReload —— 使维度网络弹药也能正常触发服务端换弹。
 */
@Mixin(value = LivingEntityReload.class, remap = false)
public class LivingEntityReloadMixin {

    /** 影射：执行换弹的实体 */
    @Shadow private LivingEntity shooter;
    /** 影射：射手数据（当前枪械、换弹状态等） */
    @Shadow private ShooterDataHolder data;
    /** 影射：收枪/拔枪逻辑 */
    @Shadow private LivingEntityDrawGun draw;
    /** 影射：射击逻辑（读取射速冷却） */
    @Shadow private LivingEntityShoot shoot;

    /** 在 reload 头部注入：校验冷却/状态后接管换弹流程，设置战术或空仓换弹状态并启动换弹 */
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
