package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.compat.tud.TudAmmoCompat;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 TACZ 物理弹药索引（按网络维护）。
 * <p>目的：把"每次推送都全桶遍历存储"降为"首次建索引一次 + delta 增量维护"。
 * 索引分两层：
 * <ul>
 *   <li>{@code PHYSICAL}：{@link ItemStackKey} → 数量（delta 需要旧值，用于算差值）；</li>
 *   <li>{@code AMMO_COUNTS}：弹药 ID → 数量（delta 时按差值累加，快照直接读，O(弹药种类)）。</li>
 * </ul>
 * 结构性变更（{@code clearStorage}/读档，只发 onChange 不发 delta）通过 any 订阅置脏，
 * 下次查询时整表重建。
 */
public final class TaczAmmoIndex {

    /** 网络ID →（弹药键 → 数量） */
    private static final Map<Integer, Map<IStackKey<?>, Long>> PHYSICAL = new ConcurrentHashMap<>();
    /** 网络ID →（弹药 ID → 聚合数量），推送/查询直接使用 */
    private static final Map<Integer, Map<String, Long>> AMMO_COUNTS = new ConcurrentHashMap<>();
    /** 已建立索引的网络 */
    private static final Set<Integer> READY = ConcurrentHashMap.newKeySet();
    /** 内容已变化、待推送的网络（事件驱动推送的唯一触发源） */
    private static final Set<Integer> DIRTY = ConcurrentHashMap.newKeySet();
    /** 网络ID → any 订阅句柄（结构性变更时置脏重建） */
    private static final Map<Integer, AutoCloseable> ANY_SUBS = new ConcurrentHashMap<>();

    private TaczAmmoIndex() {}

    /** 取出并清空待推送网络集合（服务端 tick 调用）。 */
    public static Set<Integer> drainDirty() {
        Set<Integer> dirty = new java.util.HashSet<>(DIRTY);
        DIRTY.removeAll(dirty);
        return dirty;
    }

    /** 清空待推送标记（推送关闭/镜像生效时丢弃）。 */
    public static void clearDirty() {
        DIRTY.clear();
    }

    /**
     * 获取网络弹药快照（物理弹药聚合 + 创造箱无限标记）。
     * 与旧 {@code countAllAmmoInNetwork} 返回值语义一致（{@code "*"} = 全类型无限）。
     */
    public static Map<String, Integer> snapshot(DimensionsNet net) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (net == null) return result;

        ensure(net);
        Map<String, Long> counts = AMMO_COUNTS.get(net.getId());
        if (counts != null) {
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                long amount = e.getValue() == null ? 0L : e.getValue();
                if (amount <= 0L) continue;
                result.put(e.getKey(), (int) Math.min(amount, Integer.MAX_VALUE));
            }
        }

        // 创造箱虚拟计数（delta 维护）并入；全类型创造直接短路
        if (net instanceof TaczCreativeAccessor tacz) {
            // 首次访问该网络时补齐创造箱虚拟计数（只扫一次），后续由 delta 维护
            TaczAmmoTracker.getOrCreate(net);
            Map<String, Integer> creativeCounts = tacz.getTaczCreativeCounts();
            if (creativeCounts != null) {
                if (creativeCounts.getOrDefault("*", 0) > 0) {
                    result.clear();
                    result.put("*", Integer.MAX_VALUE);
                    return result;
                }
                for (Map.Entry<String, Integer> entry : creativeCounts.entrySet()) {
                    if (!"*".equals(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0) {
                        result.put(entry.getKey(), Integer.MAX_VALUE);
                    }
                }
            }
        }
        return result;
    }

    /** 确保该网络索引已建立（首次全量扫描一次并订阅 delta/any）。 */
    public static void ensure(DimensionsNet net) {
        if (net == null) return;
        int netId = net.getId();
        if (READY.contains(netId)) return;
        synchronized (TaczAmmoIndex.class) {
            if (READY.contains(netId)) return;
            rebuild(net);
            subscribe(net);
            READY.add(netId);
        }
    }

    /** 全量重建该网络的弹药键索引与弹药 ID 聚合。 */
    private static void rebuild(DimensionsNet net) {
        Map<IStackKey<?>, Long> keys = new ConcurrentHashMap<>();
        Map<String, Long> counts = new ConcurrentHashMap<>();
        var storage = net.getUnifiedStorage();
        for (var ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            String ammoId = ammoIdOf(ik.getReadOnlyStack());
            if (ammoId == null) continue;
            if (ka.amount() > 0L) {
                keys.put(ik, ka.amount());
                counts.merge(ammoId, ka.amount(), Long::sum);
            }
        }
        PHYSICAL.put(net.getId(), keys);
        AMMO_COUNTS.put(net.getId(), counts);
    }

    /** delta：更新键级数量并按差值维护弹药 ID 聚合；any：结构性变更时置脏（下次查询重建）。 */
    private static void subscribe(DimensionsNet net) {
        int netId = net.getId();
        BdSubscriptionHub.subscribe(net, TaczAmmoIndex.class, (key, size, insert) -> {
            if (!(key instanceof ItemStackKey ik)) return;
            String ammoId = ammoIdOf(ik.getReadOnlyStack());
            if (ammoId == null) return;
            Map<IStackKey<?>, Long> keys = PHYSICAL.get(netId);
            Map<String, Long> counts = AMMO_COUNTS.get(netId);
            if (keys == null || counts == null) return;
            long oldAmount = keys.getOrDefault(ik, 0L);
            long newAmount = net.getUnifiedStorage().getStackByKey(ik).amount();
            if (newAmount <= 0L) keys.remove(ik);
            else keys.put(ik, newAmount);
            long delta = newAmount - oldAmount;
            if (delta != 0L) {
                long sum = counts.getOrDefault(ammoId, 0L) + delta;
                if (sum <= 0L) counts.remove(ammoId);
                else counts.put(ammoId, sum);
            }
            DIRTY.add(netId);
        });
        try {
            AutoCloseable old = ANY_SUBS.remove(netId);
            if (old != null) old.close();
            ANY_SUBS.put(netId, net.getUnifiedStorage().subscribeAny(TaczAmmoIndex.class, () -> {
                READY.remove(netId);
                PHYSICAL.remove(netId);
                AMMO_COUNTS.remove(netId);
                DIRTY.add(netId);
            }));
        } catch (Throwable ignored) {
        }
    }

    /** 解析物品对应的弹药 ID（TACZ 弹药 ID 或 TUD 物品弹药注册名）；非弹药返回 null。 */
    private static String ammoIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof IAmmoBox) return null;
        if (stack.getItem() instanceof IAmmo iAmmo) {
            ResourceLocation ammoId = iAmmo.getAmmoId(stack);
            return ammoId == null ? null : ammoId.toString();
        }
        if (TudAmmoCompat.isLoaded()) {
            ResourceLocation itemAmmoId = TudAmmoCompat.getItemAmmoId(stack);
            return itemAmmoId == null ? null : itemAmmoId.toString();
        }
        return null;
    }

    /** 网络销毁时清理索引与订阅句柄。 */
    public static void removeById(int netId) {
        READY.remove(netId);
        PHYSICAL.remove(netId);
        AMMO_COUNTS.remove(netId);
        DIRTY.remove(netId);
        AutoCloseable close = ANY_SUBS.remove(netId);
        if (close != null) {
            try {
                close.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 服务器停止/配置重载时清空全部索引。 */
    public static void clear() {
        READY.clear();
        PHYSICAL.clear();
        AMMO_COUNTS.clear();
        DIRTY.clear();
        for (AutoCloseable close : ANY_SUBS.values()) {
            try {
                close.close();
            } catch (Throwable ignored) {
            }
        }
        ANY_SUBS.clear();
    }
}
