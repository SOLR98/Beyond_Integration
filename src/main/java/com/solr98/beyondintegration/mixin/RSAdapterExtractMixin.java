package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.refinedmods.refinedstorage.api.util.Action;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.integration.module.rs.storage.BD_RS120ExternalStorageItemsAdapter", remap = false)
public class RSAdapterExtractMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Field beyond$beField;

    @Inject(method = "insert", at = @At("RETURN"), remap = false)
    private void beyond$onInsert(ItemStack prototype, int size, Action action, CallbackInfoReturnable<ItemStack> cir) {
        if (action == Action.SIMULATE) return;
        if (!CommandConfig.enableAuditLog()) return;
        ItemStack remainder = cir.getReturnValue();
        int inserted = size - (remainder.isEmpty() ? 0 : remainder.getCount());
        if (inserted <= 0) return;

        record(prototype.getItem().getDescriptionId(), inserted, true);
    }

    @Inject(method = "extract", at = @At("RETURN"), remap = false)
    private void beyond$onExtract(ItemStack prototype, int size, int flags, Action action,
                                  CallbackInfoReturnable<ItemStack> cir) {
        if (action == Action.SIMULATE) return;
        if (!CommandConfig.enableAuditLog()) return;
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;

        record(prototype.getItem().getDescriptionId(), result.getCount(), false);
    }

    @Unique
    private void record(String itemId, int amount, boolean insert) {
        try {
            if (beyond$beField == null) {
                beyond$beField = getClass().getClassLoader()
                        .loadClass("com.wintercogs.beyonddimensions.integration.module.rs.storage.BD_RS120ExternalStorageItemsAdapter")
                        .getDeclaredField("be");
                beyond$beField.setAccessible(true);
            }
            Object beObj = beyond$beField.get(this);
            if (!(beObj instanceof NetedBlockEntity nbe)) return;

            int netId = nbe.getNetId();
            if (netId < 0) return;

            BlockPos pos = nbe.getBlockPos();
            ResourceLocation itemRL = ResourceLocation.tryParse(itemId);
            if (itemRL != null) {
                if (insert) {
                    NetworkMeters.recordPathwayFlow(netId, pos, itemRL, amount, 0);
                } else {
                    NetworkMeters.recordPathwayFlow(netId, pos, itemRL, 0, amount);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[RS] Failed to record pathway flow", e);
        }
    }
}
