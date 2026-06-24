package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point for mods that want to hook into the BD storage insert pipeline.
 * <p>
 * Provides full context about the insertion (original and current state, target network).
 * Register via {@link com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler#addHandler}.
 * <p>
 * Unlike {@code UnifiedStorageBeforeInsertHandler.BeforeInsertHandler}, this provides
 * additional semantic context about the item being inserted (as an ItemStackKey) for convenience.
 */
public interface IBeforeInsertExtension {

    /**
     * Called before an item is inserted into a BD network.
     * @param originalInsert the original insertion request
     * @param tryInsert the current insertion state (may have been modified by previous handlers)
     * @param net the target Dimension Network, or null
     * @return the result info (modified insertion or cancellation)
     */
    @NotNull
    UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo onBeforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            @Nullable DimensionsNet net);
}
