package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.compat.AeronauticsHelper", remap = false)
public class SentryAeronauticsFixMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "isInSableSubLevel", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableCheck(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("SableCompanion not found, disabling Sable sub-level check");
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "sableSubLevelToWorld", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableToWorld(Level level, Vec3 localPos, CallbackInfoReturnable<Vec3> cir) {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        } catch (ClassNotFoundException e) {
            cir.setReturnValue(localPos);
        }
    }

    @Inject(method = "sableWorldToSubLevel", at = @At("HEAD"), cancellable = true)
    private static void beyond$fixSableToSub(Level level, Vec3 worldPos, BlockPos queryPos, CallbackInfoReturnable<Vec3> cir) {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        } catch (ClassNotFoundException e) {
            cir.setReturnValue(worldPos);
        }
    }
}
