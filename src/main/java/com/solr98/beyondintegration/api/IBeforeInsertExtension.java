package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IBeforeInsertExtension {

    @NotNull
    UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo onBeforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            @Nullable DimensionsNet net);
}
