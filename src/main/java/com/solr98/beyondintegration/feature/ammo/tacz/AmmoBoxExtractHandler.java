package com.solr98.beyondintegration.feature.ammo.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * TACZ 弹药箱插入规则：拦截普通弹药箱的插入，拆箱按弹药 ID 将子弹总量入账网络存储，
 * 并把清空后的空弹药箱（0 发）同样入库；创造弹药箱与无效弹药箱原样放行。
 */
public final class AmmoBoxExtractHandler implements InsertRule
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("beyond_integration", "tacz_ammo_box");

    public static void register()
    {
        UnifiedStorageRules.insertMatching(ID, AmmoBoxExtractHandler::matches, new AmmoBoxExtractHandler());
    }

    private static boolean matches(IStackKey<?> key)
    {
        if (!(key instanceof ItemStackKey itemKey)) return false;
        ItemStack stack = itemKey.getReadOnlyStack();
        return !stack.isEmpty() && stack.getItem() instanceof IAmmoBox;
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request)
    {
        if (request.net() == null) return null;
        if (!(request.key() instanceof ItemStackKey itemKey)) return null;

        ItemStack stack = itemKey.copyStackWithCount(1);
        if (stack.isEmpty() || !(stack.getItem() instanceof IAmmoBox iAmmoBox)) return null;

        if (iAmmoBox.isAllTypeCreative(stack) || iAmmoBox.isCreative(stack)) return null;

        ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
        int ammoCount = iAmmoBox.getAmmoCount(stack);
        if (ammoId == null || ammoCount <= 0) return null;

        ItemStack ammoStack = new ItemStack(ModItems.AMMO.get());
        if (ammoStack.getItem() instanceof IAmmo iAmmo)
        {
            iAmmo.setAmmoId(ammoStack, ammoId);
        }
        ItemStackKey ammoKey = new ItemStackKey(ammoStack);

        // 构建清空后的空弹药箱（0 发）并入账
        ItemStack emptyBox = itemKey.copyStackWithCount(1);
        if (emptyBox.getItem() instanceof IAmmoBox iEmpty)
        {
            iEmpty.setAmmoCount(emptyBox, 0);
            iEmpty.setAmmoId(emptyBox, DefaultAssets.EMPTY_AMMO_ID);
        }
        ItemStackKey emptyBoxKey = new ItemStackKey(emptyBox);

        // 每箱拆成"整箱子弹 + 1 个空箱子"，自身不存入
        return InsertPlan.convert(1)
                .add(ammoKey, ammoCount)
                .add(emptyBoxKey, 1);
    }
}
