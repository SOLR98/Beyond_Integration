package com.solr98.beyondintegration.feature.ammo.sw;
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.item.ammo.AmmoBoxItem;
import com.atsuishio.superbwarfare.item.ammo.AmmoSupplierItem;
import com.atsuishio.superbwarfare.item.ammo.CreativeAmmoBoxItem;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SuperbAmmoInsertHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!(tryInsert.key() instanceof ItemStackKey itemKey) || net == null) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        if (!(net instanceof SuperbAmmoAccessor acc)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack stack = itemKey.getReadOnlyStack();
        var item = stack.getItem();
        var map = acc.getSuperbAmmo();

        // AmmoSupplierItem → 5 种虚拟弹药 + 专用弹药盒（如 handgun_ammo_box）
        if (item instanceof AmmoSupplierItem supplier) {
            map.merge(supplier.getType().serializationName, tryInsert.amount() * supplier.getAmmoToAdd(), Long::sum);
            net.setDirty();
            return accept();
        }

        // CreativeAmmoBoxItem → 存入物理存储 + 标记无限弹药
        if (item instanceof CreativeAmmoBoxItem) {
            map.put("__infinite__", 1L);
            net.setDirty();
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // 通用 AmmoBoxItem（ammo_box）→ 提取弹药存虚拟，并清空弹药数据让空盒正常存入网络
        if (item instanceof AmmoBoxItem) {
            ItemStack boxStack = stack.copyWithCount(1);
            boolean any = false;
            for (Ammo type : Ammo.values()) {
                int count = type.get(boxStack);
                if (count > 0) {
                    map.merge(type.serializationName, (long) count, Long::sum);
                    boxStack.remove(type.dataComponent);
                    any = true;
                }
            }
            if (!any) {
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
            }
            net.setDirty();
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                    new KeyAmount(new ItemStackKey(boxStack), tryInsert.amount()), false);
        }

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo accept() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(EmptyStackKey.INSTANCE, 0), false);
    }
}
