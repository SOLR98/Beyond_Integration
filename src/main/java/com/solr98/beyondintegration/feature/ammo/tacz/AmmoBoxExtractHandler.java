package com.solr98.beyondintegration.feature.ammo.tacz;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

/**
 * TACZ 弹药箱插入拦截处理器（统一存储插入前置回调）
 * 拦截普通弹药箱的插入：拆箱按弹药 ID 将子弹总量入账网络存储，
 * 并将清空后的空弹药箱（0 发）同样入库；创造弹药箱与无效弹药箱原样放行。
 */
public class AmmoBoxExtractHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 插入前置处理：拆箱弹药箱并入账子弹与空箱
     *
     * @return 拆箱成功后返回空物品（吞掉原插入），否则原样放行
     */
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

        // 计算箱子内子弹总数并入账网络存储
        long totalAmmo = (long) ammoCount * boxCount;

        ItemStack ammoStack = new ItemStack(ModItems.AMMO.get());
        if (ammoStack.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ammoStack, ammoId);
        }

        ItemStackKey ammoKey = new ItemStackKey(ammoStack);
        long ammoLeft = net.getUnifiedStorage().insert(ammoKey, totalAmmo, false).amount();
        if (ammoLeft > 0) {
            LOGGER.warn("AmmoBoxExtract: network capacity insufficient, {} ammo could not be stored", ammoLeft);
        }

        // 构建清空后的空弹药箱（0 发）并入账
        ItemStack emptyBox = itemStackKey.copyStackWithCount(1);
        if (emptyBox.getItem() instanceof IAmmoBox iEmpty) {
            iEmpty.setAmmoCount(emptyBox, 0);
            iEmpty.setAmmoId(emptyBox, DefaultAssets.EMPTY_AMMO_ID);
        }
        ItemStackKey emptyBoxKey = new ItemStackKey(emptyBox);
        long boxLeft = net.getUnifiedStorage().insert(emptyBoxKey, boxCount, false).amount();
        if (boxLeft > 0) {
            LOGGER.warn("AmmoBoxExtract: network slot capacity insufficient, {} empty boxes could not be stored", boxLeft);
        }

        net.setDirty();

        // 吞掉原弹药箱插入（返回空物品）
        ItemStackKey emptyKey = new ItemStackKey(ItemStack.EMPTY);
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(emptyKey, 0), false);
    }
}
