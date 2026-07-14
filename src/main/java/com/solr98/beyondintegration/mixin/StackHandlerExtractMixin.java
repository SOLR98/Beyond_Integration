package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.bind.BufferTracker;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler", remap = false)
public class StackHandlerExtractMixin {

    @Inject(method = "extract(IJZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;", at = @At("RETURN"), remap = false)
    private void beyond$onExtract(int slot, long amount, boolean simulate, CallbackInfoReturnable<KeyAmount> cir) {
        if (simulate) return;
        KeyAmount result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;

        BufferTracker.BufferContext ctx = BufferTracker.get((com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler)(Object)this);
        if (ctx == null) return;

        ResourceLocation itemId = beyond$itemId(result.key());
        if (itemId != null) {
            NetworkMeters.recordInterfaceExtract(ctx.netId(), ctx.pos(), itemId, result.amount());
        }
    }

    @Unique
    private static ResourceLocation beyond$itemId(IStackKey<?> key) {
        if (key instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey ik) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ik.getSource());
        }
        return key.getTypeId();
    }
}
