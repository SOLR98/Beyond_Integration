package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ammo.tacz.NetworkAwareAmmoHandler;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 TACZ 的脚本 API ModernKineticGunScriptAPI：
 * 扩展弹药消耗逻辑——当玩家自身弹药不足时，从绑定维度网络的弹药库补充，
 * 实现"网络弹药"功能（消耗与可用性检查）。
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public class ModernKineticGunScriptAPIMixin {

    /** 射击者（玩家实体） */
    @Shadow private LivingEntity shooter;
    /** 当前使用的枪械物品 */
    @Shadow private ItemStack itemStack;

    /** 原消耗不足时，从维度网络补扣剩余弹药并累加返回值 */
    @Inject(method = "consumeAmmoFromPlayer", at = @At("RETURN"), cancellable = true)
    private void onConsumeAmmoFromPlayer(int neededAmount, CallbackInfoReturnable<Integer> cir) {
        int fromOrig = cir.getReturnValueI();
        if (fromOrig >= neededAmount) return;
        if (!(shooter instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        int fromNet = NetworkAwareAmmoHandler.consumeFromNetworks(sp, itemStack, neededAmount - fromOrig);
        if (fromNet > 0) {
            cir.setReturnValue(fromOrig + fromNet);
        }
    }

    /** 原判定无弹药时，若维度网络中有可用弹药则返回 true */
    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (shooter == null) return;

        if (NetworkAwareAmmoHandler.hasAvailable(shooter, itemStack)) {
            cir.setReturnValue(true);
        }
    }
}
