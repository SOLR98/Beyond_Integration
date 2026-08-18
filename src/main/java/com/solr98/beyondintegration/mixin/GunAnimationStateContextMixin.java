package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 TACZ 的 {@link GunAnimationStateContext}（动画状态机上下文），
 * 扩展 hasAmmoToConsume：背包弹药耗尽时，若维度网络缓存中仍有对应弹药（客户端视角），
 * 则视为可继续消耗，保证换弹/射击动画正常触发。
 */
@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {

    /** 扩展 hasAmmoToConsume：网络弹药也算作可用弹药（数据未加载时先请求缓存） */
    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return;

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;

        if (TaczAmmoCache.hasData(ammoId)) {
            if (TaczAmmoCache.getCount(ammoId) > 0) {
                cir.setReturnValue(true);
            }
        } else {
            TaczAmmoCache.requestQuick(ammoId);
            cir.setReturnValue(true);
        }
    }
}
