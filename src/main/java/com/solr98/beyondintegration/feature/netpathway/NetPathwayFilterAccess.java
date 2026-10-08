package com.solr98.beyondintegration.feature.netpathway;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;

/**
 * 维度网络通道（net_pathway）过滤器访问接口，由 {@code NetPathwayBlockEntityFilterMixin} 实现。
 */
public interface NetPathwayFilterAccess {
    boolean beyond$isFilterEnabled();

    void beyond$setFilterEnabled(boolean value);

    boolean beyond$isOnlyInput();

    void beyond$setOnlyInput(boolean value);

    boolean beyond$isFuzzy();

    void beyond$setFuzzy(boolean value);

    StackHandler beyond$getFilterSlots();

    /** 按当前过滤开关，把网络存储包装为过滤视图（未启用过滤时原样返回）。 */
    UnifiedStorage beyond$wrapStorage(UnifiedStorage original);
}
