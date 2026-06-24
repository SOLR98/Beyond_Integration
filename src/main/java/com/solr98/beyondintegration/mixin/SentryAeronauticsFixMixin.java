package com.solr98.beyondintegration.mixin;

import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper", remap = false)
public class SentryAeronauticsFixMixin {

    @Unique
    private static final boolean SABLE_LOADED = ModList.get().isLoaded("sable");

    @Inject(method = "isInSableSubLevel", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableCheck(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!SABLE_LOADED) cir.setReturnValue(false);
    }

    @Inject(method = "sableSubLevelToWorld", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableToWorld(Level level, Vec3 localPos, CallbackInfoReturnable<Vec3> cir) {
        if (!SABLE_LOADED) cir.setReturnValue(localPos);
    }

    @Inject(method = "sableWorldToSubLevel", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableToSub(Level level, Vec3 worldPos, BlockPos queryPos, CallbackInfoReturnable<Vec3> cir) {
        if (!SABLE_LOADED) cir.setReturnValue(worldPos);
    }
}
