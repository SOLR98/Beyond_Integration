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

/**
 * SW 弹药插入拦截处理器（统一存储插入前置回调）
 * 拦截向网络插入的 SW 弹药物品/弹药箱，将实体弹药折算为虚拟弹药计数
 * 存入 SuperbAmmoAccessor 并吞掉原插入（返回空物品），实现弹药不入物理存储。
 */
public class SuperbAmmoInsertHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    /** 全部 SW 弹药类型的序列化名（用于读取弹药箱 NBT 键） */
    private static final String[] AMMO_KEYS;
    static {
        // 静态初始化：枚举 SuperbWarfare 的全部弹药类型
        Ammo[] values = Ammo.values();
        AMMO_KEYS = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            AMMO_KEYS[i] = values[i].serializationName;
        }
    }

    /**
     * 插入前置处理：识别 SW 弹药并转为虚拟弹药计数入账
     *
     * @return 弹药箱拆箱/弹药折算入账后返回 accept() 吞掉插入，否则原样放行
     */
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {

        if (!(tryInsert.key() instanceof ItemStackKey itemKey)) return pass(tryInsert);
        if (net == null) return pass(tryInsert);
        if (!(net instanceof SuperbAmmoAccessor acc)) return pass(tryInsert);

        Item item = itemKey.getSource();

        // 普通弹药箱：拆箱并把箱内所有子弹类型按数量并入虚拟弹药
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

        // 创造弹药箱：放行（不折算入账）
        if (item == ModItems.CREATIVE_AMMO_BOX.get()) {
            return pass(tryInsert);
        }

        // 散装弹药/弹药盒：折算为子弹数并入账
        String ammoType = matchAmmo(item);
        if (ammoType == null) return pass(tryInsert);

        long bulletCount = toBulletCount(item, tryInsert.amount());
        if (bulletCount <= 0) return pass(tryInsert);

        acc.getSuperbAmmo().merge(ammoType, bulletCount, Long::sum);
        net.setDirty();

        return accept();
    }

    /**
     * 匹配物品对应的 SW 弹药类型名（非弹药物品返回 null）
     */
    private static String matchAmmo(Item item) {
        if (item == ModItems.HANDGUN_AMMO.get() || item == ModItems.HANDGUN_AMMO_BOX.get()) return "HandgunAmmo";
        if (item == ModItems.RIFLE_AMMO.get() || item == ModItems.RIFLE_AMMO_BOX.get()) return "RifleAmmo";
        if (item == ModItems.SHOTGUN_AMMO.get() || item == ModItems.SHOTGUN_AMMO_BOX.get()) return "ShotgunAmmo";
        if (item == ModItems.SNIPER_AMMO.get() || item == ModItems.SNIPER_AMMO_BOX.get()) return "SniperAmmo";
        if (item == ModItems.HEAVY_AMMO.get()) return "HeavyAmmo";
        return null;
    }

    /**
     * 将物品数量折算为子弹数量（弹药盒按固定倍数折算）
     */
    private static long toBulletCount(Item item, long amount) {
        if (item == ModItems.CREATIVE_AMMO_BOX.get()) return Long.MAX_VALUE;
        if (item == ModItems.RIFLE_AMMO_BOX.get() || item == ModItems.HANDGUN_AMMO_BOX.get()) return amount * 30;
        if (item == ModItems.SHOTGUN_AMMO_BOX.get() || item == ModItems.SNIPER_AMMO_BOX.get()) return amount * 12;
        return amount;
    }

    /**
     * 原样放行插入（不做处理）
     */
    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount input) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(input, false);
    }

    /**
     * 吞掉插入（返回空物品，表示该插入已被消费）
     */
    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo accept() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(EmptyStackKey.INSTANCE, 0), false);
    }
}
