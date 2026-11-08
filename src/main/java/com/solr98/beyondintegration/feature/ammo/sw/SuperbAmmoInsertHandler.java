package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.ammo.AmmoSupplierItem;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * SW 弹药的插入转化规则：把<b>盒装弹药</b>拆成<b>散装弹药物品</b>存入网络。
 * <p>
 * 网络里 SW 弹药以散装弹药物品本身存储（见 {@link SwAmmoStorage}），因此这里只处理多发的包装物品：
 * <ul>
 *   <li>{@code *_AMMO_BOX}（单类型弹药盒，1 个 = 30 / 12 发）→ 对应数量的散装弹药物品；</li>
 *   <li>{@code AMMO_BOX}（多类型弹药箱，NBT 里按类型计数）→ 每种类型各自的散装弹药物品。</li>
 * </ul>
 * 散装弹药物品（1 个 = 1 发）与创造弹药箱都原样入库，不在此规则内。
 */
public final class SuperbAmmoInsertHandler implements InsertRule
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("beyond_integration", "sw_ammo");

    public static void register()
    {
        UnifiedStorageRules.insertMatching(ID, SuperbAmmoInsertHandler::matches, new SuperbAmmoInsertHandler());
    }

    private static boolean matches(IStackKey<?> key)
    {
        if (!(key instanceof ItemStackKey itemKey)) return false;
        Item item = itemKey.getSource();
        if (item == ModItems.AMMO_BOX.get()) return true;
        // 单类型弹药盒：AmmoSupplierItem 且每个物品折算多发（散装弹药为 1）
        return item instanceof AmmoSupplierItem supplier && supplier.getAmmoToAdd() > 1;
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request)
    {
        if (!(request.key() instanceof ItemStackKey itemKey)) return null;
        Item item = itemKey.getSource();

        // 多类型弹药箱：拆成每种类型各自的散装弹药物品
        if (item == ModItems.AMMO_BOX.get())
        {
            ItemStack box = itemKey.copyStackWithCount(1);
            CompoundTag tag = com.solr98.beyondintegration.core.util.ItemNbt.read(box);
            InsertPlan plan = InsertPlan.convert(1);
            boolean any = false;
            for (Ammo ammo : Ammo.values())
            {
                int count = ammo.get(tag);
                if (count <= 0) continue;
                plan.add(SwAmmoStorage.keyOf(ammo), count);
                any = true;
            }
            return any ? plan : null;
        }

        // 单类型弹药盒：1 个 = ammoToAdd 发散装弹药
        if (item instanceof AmmoSupplierItem supplier)
        {
            int perBox = supplier.getAmmoToAdd();
            if (perBox <= 1) return null;
            Ammo type = supplier.getType();
            if (type == null) return null;
            return InsertPlan.convert(1).add(SwAmmoStorage.keyOf(type), perBox);
        }

        return null;
    }
}
