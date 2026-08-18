package com.solr98.beyondintegration.feature.ammo.tacz;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每网络一份的 TACZ 弹药查询缓存（查询层缓存，替代热路径的存储桶扫描）。
 *
 * 数据流：
 * - 首次 getOrCreate 时全量扫描一次（refresh：汇总 ammoId 数量 + 创造箱对账），
 *   之后通过 storage delta 订阅（经 BdSubscriptionHub）增量维护；
 * - 热路径（射击/装填/判定）经静态查询 API 读缓存，全部 O(1)；
 * - 扣弹成功后经 notifyConsumed 同步扣减缓存，保持即时一致；
 * - 轮询推送基于快照比较（无变化不推）；闲置超时（5 分钟）由轮询回收。
 *
 * 创造箱采用单一权威：只读 TaczCreativeAccessor（BD 统一输入输出 delta 事件维护，
 * 持久化于 NetworkAmmoData），物理扫描仅在对账（首次 refresh）时执行一次。
 */
public class TaczAmmoTracker {

    /** 网络ID -> 追踪器缓存（并发安全） */
    private static final Map<Integer, TaczAmmoTracker> TRACKERS = new ConcurrentHashMap<>();

    /** 闲置回收阈值（5 分钟，由轮询服务调用 reapIdle 清理） */
    private static final long IDLE_TIMEOUT_MS = 300_000L;

    /** 所属网络 ID */
    private final int netId;
    /** 弹药ID -> 总数（网络级汇总缓存，跨 NBT 变种聚合） */
    private final Map<String, Integer> ammoCounts = new HashMap<>();
    // 按具体 key 的增量明细（id -> key -> count），同弹药多 key 时 delta 精确修正
    private final Map<String, Map<ItemStackKey, Integer>> perKeyCounts = new HashMap<>();
    /** 是否存在全类型创造弹药箱（无限弹药标志） */
    private volatile boolean allCreative = false;
    /** 最近使用时间戳（闲置回收依据） */
    private volatile long lastUsed = System.currentTimeMillis();
    /** 上次推送快照（无变化不推的依据） */
    private Map<String, Integer> lastPushed = null;

    /** 私有构造：仅允许通过 getOrCreate 创建 */
    private TaczAmmoTracker(int netId) {
        this.netId = netId;
    }

    /**
     * 获取指定网络的追踪器（不存在则创建并完成初始全量扫描、创造箱对账与订阅）
     */
    public static TaczAmmoTracker getOrCreate(DimensionsNet net) {
        int id = net.getId();
        return TRACKERS.computeIfAbsent(id, k -> {
            TaczAmmoTracker tracker = new TaczAmmoTracker(id);
            tracker.init(net);
            return tracker;
        });
    }

    /**
     * 移除指定网络的追踪器（同时清理其统一订阅）
     */
    public static void removeById(int netId) {
        TRACKERS.remove(netId);
        BdSubscriptionHub.onNetDestroyed(netId);
    }

    /**
     * 清空全部追踪器（同时清理全部统一订阅）
     */
    public static void clear() {
        TRACKERS.clear();
        BdSubscriptionHub.clearAll();
    }

    // ───────────────────── 查询 API（热路径入口，O(1)） ─────────────────────

    /**
     * 网络可用弹药数（含创造箱无限语义；无网络/无弹药返回 0）
     * 缓存 miss 时惰性创建 tracker（一次 O(桶) 全量回填，此后全 O(1)）。
     */
    public static int countAvailable(DimensionsNet net, ResourceLocation ammoId) {
        if (net == null || ammoId == null) return 0;
        TaczAmmoTracker tracker = getOrCreate(net);
        if (tracker.isCreativeInfinite(ammoId)) return Integer.MAX_VALUE;
        synchronized (tracker.ammoCounts) {
            return tracker.ammoCounts.getOrDefault(ammoId.toString(), 0);
        }
    }

    /** 网络是否有该弹药可用（无限或数量 > 0） */
    public static boolean hasAvailable(DimensionsNet net, ResourceLocation ammoId) {
        return countAvailable(net, ammoId) > 0;
    }

    /** 网络是否有该弹药的创造箱（无限弹药，O(1) 只读虚拟计数） */
    public static boolean isInfinite(DimensionsNet net, ResourceLocation ammoId) {
        if (net == null || ammoId == null) return false;
        return getOrCreate(net).isCreativeInfinite(ammoId);
    }

    /**
     * 扣弹成功后同步扣减缓存（无缓存则跳过——后续创建时会全量重建，不会漂移）
     */
    public static void notifyConsumed(DimensionsNet net, String ammoId, int amount) {
        if (net == null || ammoId == null || amount <= 0) return;
        TaczAmmoTracker tracker = TRACKERS.get(net.getId());
        if (tracker == null) return;
        synchronized (tracker.ammoCounts) {
            int cur = tracker.ammoCounts.getOrDefault(ammoId, 0);
            int left = cur - amount;
            if (left <= 0) tracker.ammoCounts.remove(ammoId);
            else tracker.ammoCounts.put(ammoId, left);
        }
        tracker.touch();
    }

    /**
     * 闲置回收：清理不在在用网络集合内、且闲置超过 5 分钟的追踪器
     */
    public static void reapIdle(Set<Integer> activeNetIds) {
        long threshold = System.currentTimeMillis() - IDLE_TIMEOUT_MS;
        TRACKERS.entrySet().removeIf(e ->
                !activeNetIds.contains(e.getKey()) && e.getValue().lastUsed < threshold);
    }

    /** 轮询推送判断：快照自上次推送以来是否有变化（无变化不推） */
    public boolean hasChangedSinceLastPush() {
        Map<String, Integer> cur = getAllCounts();
        if (cur.equals(lastPushed)) return false;
        lastPushed = cur;
        return true;
    }

    // ───────────────────── 内部维护 ─────────────────────

    /** 初始化：全量扫描一次（含创造箱对账）并订阅 storage delta 增量事件 */
    private void init(DimensionsNet net) {
        refresh();
        reconcileCreativeBoxes(net);
        BdSubscriptionHub.subscribe(net, this, (key, size, insert) -> onDelta(net, key));
    }

    /**
     * 全量扫描网络中的 tacz:ammo 物品并汇总为列表（仅首次创建/对账兜底时调用）。
     * 返回最新快照（全类型创造箱时含 "*" → Integer.MAX_VALUE）。
     */
    public Map<String, Integer> refresh() {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return getAllCounts();
        Map<String, Integer> fresh = TaczAmmoExtractor.countAllAmmoInNetwork(net);
        synchronized (ammoCounts) {
            allCreative = false;
            ammoCounts.clear();
            perKeyCounts.clear();
            for (var entry : fresh.entrySet()) {
                if ("*".equals(entry.getKey())) {
                    allCreative = true;
                } else {
                    ammoCounts.put(entry.getKey(), entry.getValue());
                }
            }
            // 重建 per-key 明细，保证 refresh 后 delta 增量与全量快照一致
            for (var e : TaczAmmoExtractor.countAmmoByKey(net).entrySet()) {
                ItemStack stack = e.getKey().getReadOnlyStack();
                if (stack.getItem() instanceof IAmmo iAmmo) {
                    ResourceLocation ammoId = iAmmo.getAmmoId(stack);
                    if (ammoId != null)
                        perKeyCounts.computeIfAbsent(ammoId.toString(), k -> new HashMap<>()).put(e.getKey(), e.getValue());
                }
            }
        }
        touch();
        return getAllCounts();
    }

    /**
     * 创造箱对账（仅在首次创建时执行一次）：
     * 物理扫描存储中的创造弹药箱，把缺失的虚拟计数补齐（只增不删，删除走 delta 事件），
     * 修正结果 markDirty 落盘——兜底 delta 订阅建立前/存档恢复期的计数缺口。
     */
    private static void reconcileCreativeBoxes(DimensionsNet net) {
        if (!(net instanceof TaczCreativeAccessor tac)) return;
        Map<String, Integer> counts = tac.getTaczCreativeCounts();
        boolean changed = false;
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            IStackKey<?> rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            if (!(stack.getItem() instanceof IAmmoBox box)) continue;
            if (box.isAllTypeCreative(stack)) {
                if (counts.getOrDefault("*", 0) <= 0) {
                    counts.put("*", 1);
                    changed = true;
                }
            } else if (box.isCreative(stack)) {
                ResourceLocation ammoId = box.getAmmoId(stack);
                if (ammoId != null && counts.getOrDefault(ammoId.toString(), 0) <= 0) {
                    counts.put(ammoId.toString(), 1);
                    changed = true;
                }
            }
        }
        if (changed) NetworkAmmoData.markDirty();
    }

    /** 创造箱无限判定（O(1)：全类型标记 + 虚拟计数直读） */
    private boolean isCreativeInfinite(ResourceLocation ammoId) {
        touch();
        if (allCreative) return true;
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net instanceof TaczCreativeAccessor tac) {
            Map<String, Integer> counts = tac.getTaczCreativeCounts();
            return counts.getOrDefault("*", 0) > 0
                    || counts.getOrDefault(ammoId.toString(), 0) > 0;
        }
        return false;
    }

    /**
     * storage delta 回调：按具体 key 增量修正弹药汇总（网络不可达时忽略）
     */
    private void onDelta(DimensionsNet net, IStackKey<?> key) {
        if (!(key instanceof ItemStackKey ik)) return;
        ItemStack stack = ik.getReadOnlyStack();
        if (!(stack.getItem() instanceof IAmmo iAmmo)) return;
        ResourceLocation ammoId = iAmmo.getAmmoId(stack);
        if (ammoId == null) return;

        String idStr = ammoId.toString();
        KeyAmount found = net.getUnifiedStorage().getStackByKey(ik);
        int newCount = (int) Math.min(found.amount(), Integer.MAX_VALUE);

        synchronized (ammoCounts) {
            if (allCreative) return;
            Map<ItemStackKey, Integer> perKey = perKeyCounts.computeIfAbsent(idStr, k -> new HashMap<>());
            if (newCount > 0) perKey.put(ik, newCount);
            else perKey.remove(ik);
            long total = 0;
            for (int v : perKey.values()) total += v;
            if (total > 0) {
                ammoCounts.put(idStr, (int) Math.min(total, Integer.MAX_VALUE));
            } else {
                ammoCounts.remove(idStr);
                perKeyCounts.remove(idStr);
            }
        }
        touch();
    }

    /**
     * 获取当前弹药汇总快照（全类型创造箱时含 "*" -> Integer.MAX_VALUE）
     */
    public Map<String, Integer> getAllCounts() {
        synchronized (ammoCounts) {
            Map<String, Integer> result = new LinkedHashMap<>(ammoCounts);
            if (allCreative) {
                result.put("*", Integer.MAX_VALUE);
            }
            return result;
        }
    }

    /** 接触（更新最近使用时间戳） */
    private void touch() {
        lastUsed = System.currentTimeMillis();
    }
}
