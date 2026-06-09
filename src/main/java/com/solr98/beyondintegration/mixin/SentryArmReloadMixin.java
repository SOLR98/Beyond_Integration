package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.FakePlayerNetMarker;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmReloadMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "hasAnyAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasAnyAmmo(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        SentryArmBlockEntity self = (SentryArmBlockEntity) (Object) this;
        FakePlayer fp = SentryFakePlayer.get(self);
        if (fp == null) return;
        DimensionsNet net = FakePlayerNetMarker.getNet(fp);
        if (net != null) {
            LOGGER.debug("hasAnyAmmo: net#{} from marker, reporting ammo available", net.getId());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "performInstantReload", at = @At("HEAD"), cancellable = true)
    private void beyond$onReload(FakePlayer fakePlayer, IGun iGun, ItemStack gunStack,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (iGun.useInventoryAmmo(gunStack)) return;

        DimensionsNet net = FakePlayerNetMarker.getNet(fakePlayer);
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
            LOGGER.debug("performInstantReload: net#{} has creative ammo, need={}", net.getId(), need);
            need = Math.min(need, 9000);
        } else if (networkCount > 0) {
            LOGGER.debug("performInstantReload: net#{} has {} ammo, need={}", net.getId(), networkCount, need);
            need = Math.min(need, Math.min(networkCount, 9000));
        } else {
            LOGGER.debug("performInstantReload: net#{} has no ammo for {}", net.getId(), gunStack.getDisplayName().getString());
            return;
        }

        int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunStack, need, net);
        if (fromNet <= 0) {
            LOGGER.debug("performInstantReload: consumed 0 from net#{}, abort", net.getId());
            return;
        }

        LOGGER.debug("performInstantReload: consumed {} from net#{}, filled gun ({}/{} → {}/{})",
                fromNet, net.getId(), currentAmmo, maxAmmo, currentAmmo + fromNet, maxAmmo);

        iGun.setCurrentAmmoCount(gunStack, currentAmmo + fromNet);

        Bolt bolt = gunData.getBolt();
        if (bolt != Bolt.OPEN_BOLT && !iGun.hasBulletInBarrel(gunStack) && iGun.getCurrentAmmoCount(gunStack) > 0) {
            iGun.reduceCurrentAmmoCount(gunStack);
            iGun.setBulletInBarrel(gunStack, true);
        }

        cir.setReturnValue(true);
    }
}
