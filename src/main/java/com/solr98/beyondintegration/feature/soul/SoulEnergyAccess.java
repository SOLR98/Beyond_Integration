package com.solr98.beyondintegration.feature.soul;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/**
 * 网络灵魂能量访问工具（{@link SoulEnergyStackKey}）。
 * <p>供 Goety 联动使用：
 * <ul>
 *   <li>读/扣/充均基于网络"灵魂能量"键；</li>
 *   <li>long → int 一律用 {@link #MAX_SAFE_INT}（{@code Integer.MAX_VALUE - 1}）截断，避免 +1 溢出；</li>
 *   <li>扣魂以 {@code extract} 的<b>返回量</b>为准（不依赖预读，防并发虚扣）。</li>
 * </ul>
 */
public final class SoulEnergyAccess {

    /** Goety 侧 int 安全上限（与 {@code TaczAmmoExtractor.MAX_SAFE_INT} 一致）。 */
    public static final int MAX_SAFE_INT = Integer.MAX_VALUE - 1;

    private SoulEnergyAccess() {
    }

    /** 读网络灵魂（long；net 为空返回 0）。 */
    public static long getSouls(DimensionsNet net) {
        if (net == null) return 0L;
        return net.getUnifiedStorage().getStackByKey(SoulKeyResolver.current()).amount();
    }

    /** 读网络灵魂并安全截断为 int（0..MAX_SAFE_INT）。 */
    public static int getSoulsInt(DimensionsNet net) {
        return safeLongToInt(getSouls(net));
    }

    /** long → int 安全截断（0..MAX_SAFE_INT）。 */
    public static int safeLongToInt(long value) {
        if (value <= 0L) return 0;
        return (int) Math.min(value, (long) MAX_SAFE_INT);
    }

    /** 充魂（入网）。返回实际插入量（受 {@code max_souls} 限制，0=不限）。 */
    public static long insertSouls(DimensionsNet net, long amount) {
        if (net == null || amount <= 0L) return 0L;
        long cap = com.solr98.beyondintegration.CommandConfig.soulMax();
        if (cap > 0L) {
            long cur = getSouls(net);
            if (cur >= cap) return 0L;
            amount = Math.min(amount, cap - cur);
        }
        if (amount <= 0L) return 0L;
        KeyAmount remaining = net.getUnifiedStorage().insert(SoulKeyResolver.current(), amount, false);
        return amount - remaining.amount();
    }

    /** 扣魂（出网）。返回实际扣除量（以 extract 返回值为准）。 */
    public static long extractSouls(DimensionsNet net, long amount) {
        if (net == null || amount <= 0L) return 0L;
        return net.getUnifiedStorage().extract(SoulKeyResolver.current(), amount, false, false).amount();
    }

    /** 设置魂量（清空后写入）。返回写入后的实际值。 */
    public static long setSouls(DimensionsNet net, long amount) {
        if (net == null) return 0L;
        long cur = getSouls(net);
        if (cur > 0L) extractSouls(net, cur);
        if (amount > 0L) insertSouls(net, amount);
        return getSouls(net);
    }

    /**
     * HUD 灵魂条分母用的网络魂上限：返回有限上限；{@code 0} 表示"不限/未设"，HUD 回退原版 MaxArcaSouls。
     * <p>受 BD 单个资源键上限约束：网络（无序存储）的单键上限为存储的 {@code slotCapacity}
     * （默认 {@code Long.MAX_VALUE}），取 {@code min(config max_souls, slotCapacity)}。
     * <p>注意：不使用 {@link SoulEnergyStackKey#getVanillaMaxStackSize()}——那是接口方块的
     * 单次传输上限（本键为 int），并非网络存储上限。
     */
    public static long hudCap(DimensionsNet net) {
        long cfg = com.solr98.beyondintegration.CommandConfig.soulMax();
        if (cfg <= 0L || cfg == Long.MAX_VALUE) return 0L;
        long cap = cfg;
        if (net != null) {
            long slot = net.getUnifiedStorage().getSlotCapacity(0);
            if (slot > 0L) cap = Math.min(cap, slot);
        }
        return cap;
    }

    /** 网络是否已献祭激活灵魂源（未激活时网络不参与取魂）。 */
    public static boolean isActivated(DimensionsNet net) {
        if (net == null) return false;
        // 未启用"仪式激活门槛"：网络默认已激活灵魂存储
        if (!com.solr98.beyondintegration.CommandConfig.soulRequireActivation()) return true;
        return com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData.getOrCreate(net.getId()).isSoulArkActivated();
    }
}
