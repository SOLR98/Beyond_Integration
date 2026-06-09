package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LocalPlayerShoot.class, remap = false)
public class LocalPlayerShootMixin {

    @Redirect(method = "doShoot", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false))
    private int redirectMin(int a, int b, com.tacz.guns.client.resource.GunDisplayInstance display,
                            IGun iGun, ItemStack mainHandItem,
                            com.tacz.guns.resource.pojo.data.gun.GunData gunData,
                            long delay, float chargeProgress) {
        if (iGun.useInventoryAmmo(mainHandItem) && a <= 0) {
            return b;
        }
        return Math.min(a, b);
    }
}
