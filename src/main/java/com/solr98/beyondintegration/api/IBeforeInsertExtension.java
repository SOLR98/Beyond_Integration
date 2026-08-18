package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 插入前扩展接口。
 * 由外部模组实现，用于在物品/资源插入维度网络存储之前介入处理，
 * 例如修改实际插入数量、调整存储或拦截插入。
 */
public interface IBeforeInsertExtension {

    /**
     * 在插入动作执行前被调用。
     * @param originalInsert 原始请求插入的资源
     * @param tryInsert      实际尝试插入的资源
     * @param net            目标维度网络（可能为 null）
     * @return 插入处理结果信息
     */
    @NotNull
    UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo onBeforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            @Nullable DimensionsNet net);
}
