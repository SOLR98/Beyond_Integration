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

@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public class ModernKineticGunScriptAPIMixin {

    @Shadow private LivingEntity shooter;
    @Shadow private ItemStack itemStack;

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

    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (shooter == null) return;

        if (NetworkAwareAmmoHandler.hasAvailable(shooter, itemStack)) {
            cir.setReturnValue(true);
        }
    }
}
