package com.solr98.beyondintegration.feature.netpathway;

import com.solr98.beyondintegration.mixin.TypeBucketAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 白名单过滤的 {@link UnifiedStorage} 视图：仅放行标记槽样品（精确 {@code ItemStackKey}；
 * {@code fuzzy} 时忽略 NBT/组件）。{@code onlyInput} 时禁止对外提取（邻居只能向网络插入）。
 */
public class NetPathwayFilteredStorage extends UnifiedStorage {

    private final UnifiedStorage delegate;
    private final Set<IStackKey<?>> allowedKeys;
    private final boolean onlyInput;
    private final boolean fuzzy;

    public NetPathwayFilteredStorage(UnifiedStorage delegate, Set<IStackKey<?>> allowedKeys,
                                     boolean onlyInput, boolean fuzzy) {
        super(null, AbstractUnorderedStackHandler.UiTimestampPolicy.NONE, 0, 0);
        this.delegate = delegate;
        this.allowedKeys = allowedKeys;
        this.onlyInput = onlyInput;
        this.fuzzy = fuzzy;
    }

    private boolean allowed(IStackKey<?> key) {
        if (key == null) return false;
        if (fuzzy) {
            for (IStackKey<?> k : allowedKeys) {
                if (k.isSame(key)) return true;
            }
            return false;
        }
        return allowedKeys.contains(key);
    }

    /**
     * 关键：外部能力包装器（{@code ItemUnifiedStorageHandler} / {@code FluidUnifiedStorageHandler}）
     * 是通过 {@code getBucket(typeId)} 枚举资源的，因此过滤视图必须返回“过滤后的桶”，
     * 否则会拿到空桶（表现为启用过滤后邻居看不到任何物品）。
     */
    @Override
    public Optional<AbstractUnorderedStackHandler.TypeBucket> getBucket(ResourceLocation type) {
        return delegate.getBucket(type).map(src -> {
            AbstractUnorderedStackHandler.TypeBucket out = new AbstractUnorderedStackHandler.TypeBucket();
            for (int i = 0; i < src.size(); i++) {
                IStackKey<?> key = src.get(i);
                if (allowed(key)) {
                    ((TypeBucketAccessor) (Object) out).beyond$add(key);
                }
            }
            return out;
        });
    }

    @Override
    public List<KeyAmount> getStorage() {
        List<KeyAmount> out = new ArrayList<>();
        for (KeyAmount ka : delegate.getStorage()) {
            if (!ka.isEmpty() && allowed(ka.key())) {
                out.add(ka);
            }
        }
        return out;
    }

    @Override
    public @NotNull KeyAmount getStackBySlot(int slot) {
        if (slot < 0) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        int index = 0;
        for (KeyAmount ka : delegate.getStorage()) {
            if (ka.isEmpty() || !allowed(ka.key())) continue;
            if (index == slot) return ka;
            index++;
        }
        return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
    }

    @Override
    public boolean isEmpty() {
        for (KeyAmount ka : delegate.getStorage()) {
            if (!ka.isEmpty() && allowed(ka.key())) return false;
        }
        return true;
    }

    @Override
    public @NotNull KeyAmount getStackByKey(IStackKey<?> key) {
        if (!allowed(key)) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return delegate.getStackByKey(key);
    }

    @Override
    public Object getOutStackByKey(IStackKey<?> key) {
        if (!allowed(key)) return null;
        return delegate.getOutStackByKey(key);
    }

    @Override
    public @NotNull KeyAmount insert(IStackKey<?> key, long amount, boolean simulate) {
        if (!allowed(key)) return new KeyAmount(key == null ? EmptyStackKey.INSTANCE : key, amount);
        return delegate.insert(key, amount, simulate);
    }

    @Override
    public @NotNull KeyAmount extract(IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        if (onlyInput) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        if (!allowed(key)) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return delegate.extract(key, amount, simulate, false);
    }

    @Override
    public long getSlotCapacity(int slot) {
        return delegate.getSlotCapacity(slot);
    }

    @Override
    public boolean isFullSlotsSize() {
        return delegate.isFullSlotsSize();
    }

    @Override
    public void onChange() {
    }
}
