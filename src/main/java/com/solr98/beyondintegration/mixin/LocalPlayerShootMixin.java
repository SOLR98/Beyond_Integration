package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LocalPlayerShoot.class, remap = false)
public class LocalPlayerShootMixin {

    @Shadow
    private LocalPlayer player;

    @ModifyArg(method = "doShoot", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false), index = 0)
    private int fixAmmoCount(int ammoCount) {
        if (ammoCount > 0) return ammoCount;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) return ammoCount;
        if (iGun.useInventoryAmmo(stack)) {
            return Integer.MAX_VALUE;
        }
        return ammoCount;
    }
}
