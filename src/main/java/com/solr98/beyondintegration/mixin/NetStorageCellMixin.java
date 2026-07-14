package com.solr98.beyondintegration.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.integration.module.ae2.me.NetStorageCell", remap = false)
public class NetStorageCellMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos CELL_POS = new BlockPos(0, -3, 0);
    private static Field beyond$netField;
    @Unique
    private UUID beyond$cellId = UUID.randomUUID();

    @Inject(method = "insert", at = @At("HEAD"), remap = false)
    private void beyond$onInsert(AEKey what, long amount, Actionable mode, IActionSource source,
                                 CallbackInfoReturnable<Long> cir) {
        if (mode.isSimulate()) return;
        if (!CommandConfig.enableAuditLog()) return;

        int netId = resolveNetId();
        if (netId < 0) return;

        ResourceLocation itemId = aeKeyToId(what);
        if (itemId != null) {
            NetworkMeters.recordCellFlow(netId, beyond$cellId, itemId, amount, 0);
        }
    }

    @Inject(method = "extract", at = @At("HEAD"), remap = false)
    private void beyond$onExtract(AEKey what, long amount, Actionable mode, IActionSource source,
                                  CallbackInfoReturnable<Long> cir) {
        if (mode.isSimulate()) return;
        if (!CommandConfig.enableAuditLog()) return;

        int netId = resolveNetId();
        if (netId < 0) return;

        ResourceLocation itemId = aeKeyToId(what);
        if (itemId != null) {
            NetworkMeters.recordCellFlow(netId, beyond$cellId, itemId, 0, amount);
        }
    }

    @Unique
    private int resolveNetId() {
        try {
            Field storageField = getClass().getClassLoader().loadClass(
                    "com.wintercogs.beyonddimensions.integration.module.ae2.me.NetStorageCell")
                    .getDeclaredField("storage");
            storageField.setAccessible(true);
            UnifiedStorage storage = (UnifiedStorage) storageField.get(this);
            if (storage == null) return -1;

            if (beyond$netField == null) {
                beyond$netField = UnifiedStorage.class.getDeclaredField("net");
                beyond$netField.setAccessible(true);
            }
            Object netObj = beyond$netField.get(storage);
            if (netObj == null) return -1;
            java.lang.reflect.Method getId = netObj.getClass().getMethod("getId");
            return (int) getId.invoke(netObj);
        } catch (Exception e) {
            return -1;
        }
    }

    @Unique
    private static ResourceLocation aeKeyToId(AEKey key) {
        return key.getId();
    }
}
