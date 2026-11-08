package com.solr98.beyondintegration.feature.ammo.sw;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * SW 弹药的网络存取（弹药物品口径）。
 * <p>
 * 网络里的 SW 弹药<b>就是散装弹药物品本身</b>（如 {@code superbwarfare:handgun_ammo}）：
 * 一个物品 = 一发（{@code AmmoSupplierItem.getAmmoToAdd() == 1}）。盒装弹药在插入时由
 * {@link SuperbAmmoInsertHandler} 按转化规则拆成对应数量的散装弹药物品，不再维护虚拟计数。
 * <p>
 * 无限弹药（创造弹药箱）同样以物品形式存在网络中，按下述键精确查询判定。
 */
public final class SwAmmoStorage {

    /** 全量快照里表示“无限弹药”的伪键（与客户端 {@code SuperbAmmoCache} 协议保持一致） */
    public static final String INFINITE_KEY = "__infinite__";

    private SwAmmoStorage() {
    }

    /** 某类弹药的散装弹药物品键（一个 = 一发）。 */
    public static ItemStackKey keyOf(Ammo ammo) {
        return new ItemStackKey(ammo.getItemStack(1));
    }

    /** 创造弹药箱物品键（存在即视为该网络无限弹药）。 */
    public static ItemStackKey creativeBoxKey() {
        return new ItemStackKey(new ItemStack(ModItems.CREATIVE_AMMO_BOX.get()));
    }

    /** 网络内该类型弹药的可用发数。 */
    public static long count(DimensionsNet net, Ammo ammo) {
        if (net == null || ammo == null) return 0L;
        return net.getUnifiedStorage().getStackByKey(keyOf(ammo)).amount();
    }

    /** 网络内是否有创造弹药箱（无限弹药）。 */
    public static boolean hasInfinite(DimensionsNet net) {
        if (net == null) return false;
        return net.getUnifiedStorage().getStackByKey(creativeBoxKey()).amount() > 0L;
    }

    /**
     * 从网络消耗指定数量弹药（无限弹药时不扣，直接满足）。
     *
     * @return 实际消耗的发数
     */
    public static long consume(DimensionsNet net, Ammo ammo, long amount) {
        if (net == null || ammo == null || amount <= 0L) return 0L;
        if (hasInfinite(net)) return amount;
        return net.getUnifiedStorage().extract(keyOf(ammo), amount, false, false).amount();
    }

    /**
     * 全量快照（弹药 serializationName → 发数；含 {@link #INFINITE_KEY}）。
     * 供 HUD / 载具同步沿用原有报文协议。
     */
    public static Map<String, Long> snapshot(DimensionsNet net) {
        Map<String, Long> out = new HashMap<>();
        if (net == null) return out;
        for (Ammo ammo : Ammo.values()) {
            long n = count(net, ammo);
            if (n > 0L) out.put(ammo.serializationName, n);
        }
        if (hasInfinite(net)) out.put(INFINITE_KEY, 1L);
        return out;
    }
}
