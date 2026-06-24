package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryFakePlayerSyncMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Leuphy/upo/sentrymechanicalarm/content/SentryArmBlockEntity;sentryLogic()V",
            shift = At.Shift.BEFORE), remap = false)
    private void beyond$beforeSentryLogic(CallbackInfo ci) {
        SentryArmBlockEntity self = (SentryArmBlockEntity) (Object) this;
        FakePlayer fp = SentryFakePlayer.get(self);
        if (fp == null) return;
        SentryFakePlayerNetMarker.markFromBoxes(fp, self.attachedAmmoBoxes);
    }
}
