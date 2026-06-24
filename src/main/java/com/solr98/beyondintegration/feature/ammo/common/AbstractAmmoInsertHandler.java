package com.solr98.beyondintegration.feature.ammo.common;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.item.ItemStack;

/**
 * Base class for handlers that intercept ammo-related items being inserted into BD networks.
 * <p>
 * Handles the common pattern of checking {@link ItemStackKey}, null network, and providing
 * convenience methods for pass/accept/transform return values.
 * Subclasses implement {@link #handleItemInsert} with mod-specific ammo extraction logic.
 */
public abstract class AbstractAmmoInsertHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    @Override
    public final @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!(tryInsert.key() instanceof ItemStackKey itemKey) || net == null) {
            return pass(tryInsert);
        }
        return handleItemInsert(originalInsert, tryInsert, itemKey, net);
    }

    protected abstract @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo handleItemInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            @NotNull ItemStackKey itemKey,
            DimensionsNet net);

    protected static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount input) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(input, false);
    }

    protected static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo accept() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(EmptyStackKey.INSTANCE, 0), false);
    }

    protected static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo transform(ItemStack newStack, long amount) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(newStack), amount), false);
    }
}
