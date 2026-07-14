package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.bind.ExtractFlag;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.UUID;

@Mixin(targets = "com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler", remap = false)
public class UnifiedStorageExtractMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("beyond_meter");
    private static final BlockPos GUI_POS = new BlockPos(0, -1, 0);
    private static final BlockPos AMMO_POS = new BlockPos(0, -2, 0);
    private static final BlockPos MAID_POS = new BlockPos(0, -4, 0);
    private static Field beyond$netField;

    @Inject(method = "extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;",
            at = @At("RETURN"))
    private void beyond$onExtract(IStackKey<?> key, long amount, boolean simulate, boolean fuzzy,
                                  CallbackInfoReturnable<KeyAmount> cir) {
        if (simulate) return;
        KeyAmount result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;

        if (!((Object) this instanceof UnifiedStorage)) return;

        // Determine source position
        ExtractFlag.Source src = ExtractFlag.getSource();
        BlockPos recordPos;
        switch (src) {
            case AMMO_CONSUMPTION -> {
                recordPos = ExtractFlag.getFlagPos();
                if (recordPos == null) recordPos = AMMO_POS;
            }
            case MAID_CONSUMPTION -> {
                recordPos = ExtractFlag.getFlagPos();
                if (recordPos == null) recordPos = MAID_POS;
                UUID maidUuid = ExtractFlag.getMaidUuid();
                String maidName = ExtractFlag.getMaidName();
                if (maidUuid != null) {
                    ResourceLocation itemId = beyond$extractItemId(key);
                    int netId = beyond$resolveNetId();
                    if (netId >= 0 && itemId != null) {
                        NetworkMeters.recordMaidExtract(netId, maidUuid, maidName, itemId, result.amount());
                    }
                }
                return;
            }
            case NET_INTERFACE -> { return; }
            default -> recordPos = GUI_POS;
        }

        int netId = beyond$resolveNetId();
        if (netId < 0) return;

        ResourceLocation itemId = beyond$extractItemId(key);
        if (itemId != null) {
            NetworkMeters.recordInterfaceExtract(netId, recordPos, itemId, result.amount());
        }
    }

    @Unique
    private static ResourceLocation beyond$extractItemId(IStackKey<?> key) {
        if (key instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey ik) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ik.getSource());
        }
        return key.getTypeId();
    }

    @Unique
    private int beyond$resolveNetId() {
        try {
            if (beyond$netField == null) {
                beyond$netField = UnifiedStorage.class.getDeclaredField("net");
                beyond$netField.setAccessible(true);
            }
            Object netObj = beyond$netField.get((Object) this);
            if (netObj == null) return -1;
            java.lang.reflect.Method getId = netObj.getClass().getMethod("getId");
            return (int) getId.invoke(netObj);
        } catch (Exception e) {
            LOGGER.error("[US] Failed to resolve netId", e);
            return -1;
        }
    }
}
