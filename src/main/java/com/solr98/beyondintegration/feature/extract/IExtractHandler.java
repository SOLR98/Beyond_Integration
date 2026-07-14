package com.solr98.beyondintegration.feature.extract;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

import javax.annotation.Nullable;

@FunctionalInterface
public interface IExtractHandler {
    @Nullable
    KeyAmount handleExtract(DimensionsNet net, UnifiedStorage storage, IStackKey<?> key, long amount, boolean simulate, boolean fuzzy);
}
