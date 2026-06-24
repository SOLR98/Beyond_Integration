package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.client.animation.statemachine.GunAnimationStateContext", remap = false)
public class GunAnimationStateContextMixin {
    @Shadow(remap = false) private net.minecraft.world.item.ItemStack currentGunItem;

    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(currentGunItem);
        if (ammoId != null) {
            if (!TaczAmmoCache.hasData(ammoId))
                TaczAmmoCache.requestQuick(ammoId);
            if (!TaczAmmoCache.hasData(ammoId) || TaczAmmoCache.getCount(ammoId) > 0)
                cir.setReturnValue(true);
        } else {
            cir.setReturnValue(true);
        }
    }
}
