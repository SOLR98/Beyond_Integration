package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.cache.AmmoCountCache;
import com.solr98.beyondintegration.handler.NetworkAmmoExtractor;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {

    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return;

        ResourceLocation ammoId = NetworkAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;

        if (AmmoCountCache.hasData(ammoId) && AmmoCountCache.getCount(ammoId) > 0) {
            cir.setReturnValue(true);
        } else if (!AmmoCountCache.hasData(ammoId)) {
            AmmoCountCache.requestQuick(ammoId);
        }
    }
}
