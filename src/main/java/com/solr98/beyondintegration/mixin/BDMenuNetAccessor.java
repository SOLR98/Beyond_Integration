package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.INetMenuAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Field;

/**
 * 注入 BeyondDimensions 的 DimensionsNetMenu（通过 targets 指定内部类/混淆名），
 * 实现 INetMenuAccessor：通过 Shadow 的 storage 字段以反射取得其绑定的 DimensionsNet，
 * 供本模组在服务端菜单逻辑中获取当前网络。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu", remap = false)
public abstract class BDMenuNetAccessor implements INetMenuAccessor {

    /** 影射目标菜单的 storage 字段（统一存储处理器） */
    @Shadow(remap = false)
    public AbstractUnorderedStackHandler storage;

    /** 缓存的 UnifiedStorage#net 反射字段，避免重复查找 */
    private static Field beyond$netField;

    /** 从 storage 反射获取其内部持有的 DimensionsNet，失败时返回 null */
    @Override
    public DimensionsNet getBoundNet() {
        if (!(storage instanceof UnifiedStorage us)) return null;
        try {
            if (beyond$netField == null) {
                beyond$netField = UnifiedStorage.class.getDeclaredField("net");
                beyond$netField.setAccessible(true);
            }
            return (DimensionsNet) beyond$netField.get(us);
        } catch (Exception e) {
            return null;
        }
    }
}
