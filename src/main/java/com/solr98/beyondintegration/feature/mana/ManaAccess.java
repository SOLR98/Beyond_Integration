package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/**
 * 网络魔力访问工具（{@link ManaStackKey}）。
 * <p>供 Iron's Spells 'n Spellbooks 联动使用：
 * <ul>
 *   <li>读/扣/充均基于网络"魔力"键；</li>
 *   <li>long → int 一律用 {@link #MAX_SAFE_INT}（{@code Integer.MAX_VALUE - 1}）截断，避免 +1 溢出；</li>
 *   <li>扣取以 {@code extract} 的<b>返回量</b>为准（不依赖预读，防并发虚扣）。</li>
 * </ul>
 */
public final class ManaAccess {

    /** 铁魔法侧 float 安全上限对齐值（同 {@code TaczAmmoExtractor.MAX_SAFE_INT}）。 */
    public static final int MAX_SAFE_INT = Integer.MAX_VALUE - 1;

    private ManaAccess() {
    }

    /** 读网络魔力（long；net 为空返回 0）。 */
    public static long getMana(DimensionsNet net) {
        if (net == null) return 0L;
        return net.getUnifiedStorage().getStackByKey(ManaStackKey.INSTANCE).amount();
    }

    /** 读网络魔力并安全截断为 int（0..MAX_SAFE_INT）。 */
    public static int getManaInt(DimensionsNet net) {
        return safeLongToInt(getMana(net));
    }

    /** long → int 安全截断（0..MAX_SAFE_INT）。 */
    public static int safeLongToInt(long value) {
        if (value <= 0L) return 0;
        return (int) Math.min(value, (long) MAX_SAFE_INT);
    }

    /** 充魔力（入网）。返回实际插入量（受 {@code max_mana} 限制，0=不限）。 */
    public static long insertMana(DimensionsNet net, long amount) {
        if (net == null || amount <= 0L) return 0L;
        long cap = CommandConfig.ironsManaMax();
        if (cap > 0L) {
            long cur = getMana(net);
            if (cur >= cap) return 0L;
            amount = Math.min(amount, cap - cur);
        }
        if (amount <= 0L) return 0L;
        KeyAmount remaining = net.getUnifiedStorage().insert(ManaStackKey.INSTANCE, amount, false);
        return amount - remaining.amount();
    }

    /** 扣魔力（出网）。返回实际扣除量（以 extract 返回值为准）。 */
    public static long extractMana(DimensionsNet net, long amount) {
        if (net == null || amount <= 0L) return 0L;
        return net.getUnifiedStorage().extract(ManaStackKey.INSTANCE, amount, false, false).amount();
    }

    /** 设置魔力（清空后写入）。返回写入后的实际值。 */
    public static long setMana(DimensionsNet net, long amount) {
        if (net == null) return 0L;
        long cur = getMana(net);
        if (cur > 0L) extractMana(net, cur);
        if (amount > 0L) insertMana(net, amount);
        return getMana(net);
    }
}
