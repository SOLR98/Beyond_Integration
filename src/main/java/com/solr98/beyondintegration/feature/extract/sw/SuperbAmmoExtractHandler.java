package com.solr98.beyondintegration.feature.extract.sw;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.extract.IExtractHandler;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

public class SuperbAmmoExtractHandler implements IExtractHandler {

    private static final String MOD_ID = "superbwarfare";

    @Override
    @Nullable
    public KeyAmount handleExtract(DimensionsNet net, UnifiedStorage storage, IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        if (!ModList.get().isLoaded(MOD_ID) || net == null || !(net instanceof SuperbAmmoAccessor acc)) {
            return null;
        }
        if (!(key instanceof ItemStackKey itemKey)) {
            return null;
        }

        ResourceLocation regId = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
        if (regId == null) {
            return null;
        }

        String ammoTypeName = resolveAmmoType(regId.getNamespace(), regId.getPath());
        if (ammoTypeName == null) {
            return null;
        }

        var ammoMap = acc.getSuperbAmmo();
        long available = ammoMap.getOrDefault(ammoTypeName, 0L);
        if (available <= 0) {
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        }

        long toExtract = Math.min(available, amount);
        if (simulate) {
            return new KeyAmount(
                    new ItemStackKey(new ItemStack(itemKey.getSource(), (int) Math.min(toExtract, 9999))),
                    toExtract);
        }

        long remaining = available - toExtract;
        if (remaining <= 0) {
            ammoMap.remove(ammoTypeName);
        } else {
            ammoMap.put(ammoTypeName, remaining);
        }
        net.setDirty();

        ItemStack resultStack = new ItemStack(itemKey.getSource(), (int) Math.min(toExtract, 9999));
        return new KeyAmount(new ItemStackKey(resultStack), toExtract);
    }

    private static String resolveAmmoType(String namespace, String path) {
        String prefix = namespace + ":" + path + ":";
        for (String entry : CommandConfig.ammoExtractMappings()) {
            if (entry.startsWith(prefix)) {
                return entry.substring(prefix.length());
            }
        }
        return null;
    }
}
