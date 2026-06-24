package com.solr98.beyondintegration.feature.ammo.tacz;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.init.ModItems;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.HashSet;
import java.util.Set;

public class AmmoBoxExtractHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!(tryInsert.key() instanceof ItemStackKey itemKey))
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        ItemStack stack = itemKey.copyStackWithCount(1);
        if (!(stack.getItem() instanceof IAmmoBox iAmmoBox))
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        if (net == null)
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        // 创造弹药箱 → 存入物理存储 + 设置 TACZ 创造标记
        if (iAmmoBox.isAllTypeCreative(stack) || iAmmoBox.isCreative(stack)) {
            if (net instanceof TaczCreativeAccessor acc) {
                Set<String> types = new HashSet<>();
                if (iAmmoBox.isAllTypeCreative(stack)) {
                    types.add("*");
                } else {
                    ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
                    if (ammoId != null) types.add(ammoId.toString());
                }
                acc.setTaczCreativeTypes(types);
            }
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
        int ammoCount = iAmmoBox.getAmmoCount(stack);
        long boxCount = originalInsert.amount();

        if (ammoId == null || boxCount <= 0 || ammoCount <= 0)
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        long totalAmmo = (long) ammoCount * boxCount;

        ItemStack ammoStack = new ItemStack(ModItems.AMMO.get());
        if (ammoStack.getItem() instanceof IAmmo iAmmo)
            iAmmo.setAmmoId(ammoStack, ammoId);
        net.getUnifiedStorage().insert(new ItemStackKey(ammoStack), totalAmmo, false);

        ItemStack emptyBox = itemKey.copyStackWithCount(1);
        if (emptyBox.getItem() instanceof IAmmoBox iEmpty) {
            iEmpty.setAmmoCount(emptyBox, 0);
            iEmpty.setAmmoId(emptyBox, DefaultAssets.EMPTY_AMMO_ID);
        }
        net.getUnifiedStorage().insert(new ItemStackKey(emptyBox), boxCount, false);
        net.setDirty();

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(ItemStack.EMPTY), 0), false);
    }
}
