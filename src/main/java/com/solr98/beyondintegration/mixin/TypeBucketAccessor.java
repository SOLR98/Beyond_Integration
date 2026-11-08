package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 BD 无序存储类型桶 {@link AbstractUnorderedStackHandler.TypeBucket} 的包私有 {@code add}，
 * 供网络通道过滤视图（{@code NetPathwayFilteredStorage}）构造“过滤后的桶”使用。
 */
@Mixin(value = AbstractUnorderedStackHandler.TypeBucket.class, remap = false)
public interface TypeBucketAccessor {

    @Invoker("add")
    void beyond$add(IStackKey<?> key);
}
