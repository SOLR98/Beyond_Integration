package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public class DimensionsNetCreateMixin {

    @Inject(method = "createNewNetForPlayer", at = @At("RETURN"), remap = false)
    private static void beyond$onCreate(Player player, long slotCap, int slotMaxSize,
                                        CallbackInfoReturnable<DimensionsNet> cir) {
        if (!CommandConfig.enableAuditLog()) return;
        DimensionsNet net = cir.getReturnValue();
        if (net == null) return;
        BindingAuditLog.log(new AuditEntry(
                System.currentTimeMillis(), "NET_CREATE",
                player.getName().getString(), player.getUUID(),
                net.getId(), "-", "-",
                true, "Created by " + player.getName().getString()
        ));
    }
}
