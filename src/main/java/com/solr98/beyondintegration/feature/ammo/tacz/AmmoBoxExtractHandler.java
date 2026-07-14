package com.solr98.beyondintegration.feature.ammo.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AmmoBoxExtractHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {

        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack stack = itemStackKey.copyStackWithCount(1);
        if (stack.isEmpty() || !(stack.getItem() instanceof IAmmoBox iAmmoBox)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (net == null) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (iAmmoBox.isAllTypeCreative(stack) || iAmmoBox.isCreative(stack)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
        int ammoCount = iAmmoBox.getAmmoCount(stack);
        long boxCount = originalInsert.amount();

        if (ammoId == null || boxCount <= 0) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (ammoCount <= 0) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        long totalAmmo = (long) ammoCount * boxCount;

        ItemStack ammoStack = new ItemStack(ModItems.AMMO.get());
        if (ammoStack.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ammoStack, ammoId);
        }

        ItemStackKey ammoKey = new ItemStackKey(ammoStack);
        net.getUnifiedStorage().insert(ammoKey, totalAmmo, false);

        ItemStack emptyBox = itemStackKey.copyStackWithCount(1);
        if (emptyBox.getItem() instanceof IAmmoBox iEmpty) {
            iEmpty.setAmmoCount(emptyBox, 0);
            iEmpty.setAmmoId(emptyBox, DefaultAssets.EMPTY_AMMO_ID);
        }
        ItemStackKey emptyBoxKey = new ItemStackKey(emptyBox);
        net.getUnifiedStorage().insert(emptyBoxKey, boxCount, false);

        net.setDirty();

        ItemStackKey emptyKey = new ItemStackKey(ItemStack.EMPTY);
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(emptyKey, 0), false);
    }
}
