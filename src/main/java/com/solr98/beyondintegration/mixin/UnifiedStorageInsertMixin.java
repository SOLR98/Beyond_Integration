package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.bind.InsertContext;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler", remap = false)
public class UnifiedStorageInsertMixin {

    private static Field beyond$netField;

    @Inject(method = "insert(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;",
            at = @At("RETURN"), remap = false)
    private void beyond$onInsert(IStackKey<?> key, long amount, boolean simulate,
                                 CallbackInfoReturnable<KeyAmount> cir) {
        if (simulate) return;
        if (!(((Object) this) instanceof UnifiedStorage)) return;

        InsertContext.Context ctx = InsertContext.get();
        if (ctx == null) return;

        KeyAmount result = cir.getReturnValue();
        if (result == null) return;
        long inserted = amount - result.amount();
        if (inserted <= 0) return;

        int netId = resolveNetId();
        if (netId < 0) return;

        ResourceLocation itemId = extractItemId(key);
        if (itemId == null) return;

        switch (ctx.machineType()) {
            case "PUMP" -> NetworkMeters.recordPumpInsert(netId, ctx.pos(), itemId, inserted);
            case "HOPPER" -> NetworkMeters.recordHopperCollect(netId, ctx.pos(), itemId, inserted);
            case "FURNACE" -> NetworkMeters.recordFurnaceSmelt(netId, ctx.pos(), itemId, inserted);
        }
    }

    @Unique
    private int resolveNetId() {
        try {
            if (beyond$netField == null) {
                beyond$netField = UnifiedStorage.class.getDeclaredField("net");
                beyond$netField.setAccessible(true);
            }
            Object netObj = beyond$netField.get(this);
            if (netObj == null) return -1;
            java.lang.reflect.Method getId = netObj.getClass().getMethod("getId");
            return (int) getId.invoke(netObj);
        } catch (Exception e) {
            return -1;
        }
    }

    @Unique
    private static ResourceLocation extractItemId(IStackKey<?> key) {
        if (key instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey ik) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ik.getSource());
        }
        return key.getTypeId();
    }
}
