package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.init.ModItems;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SuperbAmmoInsertHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final String[] AMMO_KEYS;
    static {
        Ammo[] values = Ammo.values();
        AMMO_KEYS = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            AMMO_KEYS[i] = values[i].serializationName;
        }
    }

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {

        if (!(tryInsert.key() instanceof ItemStackKey itemKey)) return pass(tryInsert);
        if (net == null) return pass(tryInsert);
        if (!(net instanceof SuperbAmmoAccessor acc)) return pass(tryInsert);

        Item item = itemKey.getSource();

        if (item == ModItems.AMMO_BOX.get()) {
            ItemStack boxStack = itemKey.copyStackWithCount(1);
            CompoundTag tag = boxStack.getOrCreateTag();
            boolean any = false;
            for (String ammoKey : AMMO_KEYS) {
                if (!tag.contains(ammoKey)) continue;
                int count = tag.getInt(ammoKey);
                if (count > 0) {
                    acc.getSuperbAmmo().merge(ammoKey, (long) count, Long::sum);
                    any = true;
                }
            }
            if (any) net.setDirty();
            return accept();
        }

        if (item == ModItems.CREATIVE_AMMO_BOX.get()) {
            return pass(tryInsert);
        }

        String ammoType = matchAmmo(item);
        if (ammoType == null) return pass(tryInsert);

        long bulletCount = toBulletCount(item, tryInsert.amount());
        if (bulletCount <= 0) return pass(tryInsert);

        acc.getSuperbAmmo().merge(ammoType, bulletCount, Long::sum);
        net.setDirty();

        return accept();
    }

    private static String matchAmmo(Item item) {
        if (item == ModItems.HANDGUN_AMMO.get() || item == ModItems.HANDGUN_AMMO_BOX.get()) return "HandgunAmmo";
        if (item == ModItems.RIFLE_AMMO.get() || item == ModItems.RIFLE_AMMO_BOX.get()) return "RifleAmmo";
        if (item == ModItems.SHOTGUN_AMMO.get() || item == ModItems.SHOTGUN_AMMO_BOX.get()) return "ShotgunAmmo";
        if (item == ModItems.SNIPER_AMMO.get() || item == ModItems.SNIPER_AMMO_BOX.get()) return "SniperAmmo";
        if (item == ModItems.HEAVY_AMMO.get()) return "HeavyAmmo";
        return null;
    }

    private static long toBulletCount(Item item, long amount) {
        if (item == ModItems.CREATIVE_AMMO_BOX.get()) return Long.MAX_VALUE;
        if (item == ModItems.RIFLE_AMMO_BOX.get() || item == ModItems.HANDGUN_AMMO_BOX.get()) return amount * 30;
        if (item == ModItems.SHOTGUN_AMMO_BOX.get() || item == ModItems.SNIPER_AMMO_BOX.get()) return amount * 12;
        return amount;
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount input) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(input, false);
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo accept() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(EmptyStackKey.INSTANCE, 0), false);
    }
}
