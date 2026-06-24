package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmReloadMixin {

    @Inject(method = "hasAnyAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasAnyAmmo(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        SentryArmBlockEntity self = (SentryArmBlockEntity) (Object) this;
        FakePlayer fp = SentryFakePlayer.get(self);
        if (fp == null) return;
        DimensionsNet net = SentryFakePlayerNetMarker.getNet(fp);
        if (net != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "performInstantReload", at = @At("HEAD"), cancellable = true)
    private void beyond$onReload(FakePlayer fakePlayer, IGun iGun, ItemStack gunStack,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (iGun.useInventoryAmmo(gunStack)) return;

        DimensionsNet net = SentryFakePlayerNetMarker.getNet(fakePlayer);
        if (net == null) return;

        var gunIndexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack));
        if (gunIndexOpt.isEmpty()) return;

        GunData gunData = gunIndexOpt.get().getGunData();
        int maxAmmo = gunData.getAmmoAmount();
        int currentAmmo = iGun.getCurrentAmmoCount(gunStack);
        int need = maxAmmo - currentAmmo;
        if (need <= 0) return;

        int networkCount = TaczAmmoExtractor.countAmmoInNetwork(gunStack, net);
        if (networkCount == TaczAmmoExtractor.CREATIVE_SENTINEL) {
            need = Math.min(need, 9000);
        } else if (networkCount > 0) {
            need = Math.min(need, Math.min(networkCount, 9000));
        } else {
            return;
        }

        int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunStack, need, net);
        if (fromNet <= 0) {
            return;
        }

        iGun.setCurrentAmmoCount(gunStack, currentAmmo + fromNet);

        Bolt bolt = gunData.getBolt();
        if (bolt != Bolt.OPEN_BOLT && !iGun.hasBulletInBarrel(gunStack) && iGun.getCurrentAmmoCount(gunStack) > 0) {
            iGun.reduceCurrentAmmoCount(gunStack);
            iGun.setBulletInBarrel(gunStack, true);
        }

        cir.setReturnValue(true);
    }
}
