package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.INetMenuAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Field;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu", remap = false)
public abstract class BDMenuNetAccessor implements INetMenuAccessor {

    @Shadow(remap = false)
    public AbstractUnorderedStackHandler storage;

    private static Field beyond$netField;

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
