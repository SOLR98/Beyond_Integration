package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.cache.AmmoCountCache;
import com.solr98.beyondintegration.handler.NetworkAmmoExtractor;
import com.solr98.beyondintegration.handler.NetworkAmmoHandler;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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

    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        if (shooter instanceof ServerPlayer) {
            if (NetworkAmmoHandler.hasNetworkAmmo(shooter, itemStack)) {
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide) {
            ResourceLocation ammoId = NetworkAmmoExtractor.getAmmoIdClient(itemStack);
            if (ammoId == null) return;
            if (AmmoCountCache.hasData(ammoId) && AmmoCountCache.getCount(ammoId) > 0) {
                cir.setReturnValue(true);
            } else if (!AmmoCountCache.hasData(ammoId)) {
                AmmoCountCache.requestQuick(ammoId);
            }
        }
    }
}
