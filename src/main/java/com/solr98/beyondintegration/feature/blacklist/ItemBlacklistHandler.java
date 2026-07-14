package com.solr98.beyondintegration.feature.blacklist;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class ItemBlacklistHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net) {

        if (!CommandConfig.enableItemBlacklist()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack itemStack = itemStackKey.getReadOnlyStack();
        if (itemStack.isEmpty()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if (itemId == null) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        for (String entry : CommandConfig.itemBlacklist()) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String entryKey = entry.contains(":") ? entry : "minecraft:" + entry;
            if (entryKey.equals(itemId.toString())) {
                LOGGER.info("Blocked insertion of blacklisted item {} x{} into network {}",
                        itemId, tryInsert.amount(), net != null ? net.getId() : "?");
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, true);
            }
        }

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }
}
