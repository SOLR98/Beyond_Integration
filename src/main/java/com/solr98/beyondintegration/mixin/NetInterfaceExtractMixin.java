package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.bind.ExtractFlag;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.solr98.beyondintegration.feature.extract.ExtractHandlerRegistry;
import com.solr98.beyondintegration.feature.extract.IExtractHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity", remap = false)
public class NetInterfaceExtractMixin {

    @Redirect(method = "transferFromNet",
              at = @At(value = "INVOKE",
                       target = "Lcom/wintercogs/beyonddimensions/api/dimensionnet/UnifiedStorage;extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;"),
              remap = false)
    private KeyAmount redirectExtract(UnifiedStorage storage, IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        DimensionsNet net = null;
        BlockPos pos = BlockPos.ZERO;
        try {
            NetInterfaceBlockEntity be = (NetInterfaceBlockEntity) (Object) this;
            net = be.getNet();
            pos = be.getBlockPos();
        } catch (Exception ignored) {}

        ExtractFlag.set(ExtractFlag.Source.NET_INTERFACE);
        try {
            KeyAmount result = null;
            for (IExtractHandler handler : ExtractHandlerRegistry.getHandlers()) {
                result = handler.handleExtract(net, storage, key, amount, simulate, fuzzy);
                if (result != null) break;
            }
            if (result == null) {
                result = storage.extract(key, amount, simulate, fuzzy);
            }
            if (!simulate && net != null && result != null && !result.isEmpty()) {
                ResourceLocation itemId = extractItemId(key);
                if (itemId != null) {
                    NetworkMeters.recordInterfaceExtract(net.getId(), pos, itemId, result.amount());
                }
            }
            return result;
        } finally {
            ExtractFlag.clear();
        }
    }

    private static ResourceLocation extractItemId(IStackKey<?> key) {
        if (key instanceof com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey ik) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ik.getSource());
        }
        return key.getTypeId();
    }
}
