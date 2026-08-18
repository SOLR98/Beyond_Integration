package com.solr98.beyondintegration.feature.ammo.sw;

import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每网络一份的 SW 固定项（虚拟弹药 + FE + 网络名 + 附魔开关）快照追踪器。
 * 虚拟弹药存储于 NetworkAmmoData（无事件源），因此采用"对账差分"：
 * 每次 drain 对比当前快照与上次快照，产出增量（delta）或全量。
 * storage delta 订阅仅用于能量/物理物品的即时脏标记。
 */
public class SwAmmoTracker {

    /** 网络ID -> 追踪器缓存（并发安全） */
    private static final Map<Integer, SwAmmoTracker> TRACKERS = new ConcurrentHashMap<>();

    /** 所属网络 ID */
    private final int netId;
    /** 上次快照：SW 弹药类型 -> 数量 */
    private final Map<String, Long> lastAmmo = new HashMap<>();
    /** 上次快照：网络 FE 能量（-1 表示未初始化，首次 drain 输出全量） */
    private long lastEnergy = -1;
    /** 上次快照：网络自定义名称 */
    private String lastName = null;
    /** 上次快照：附魔分离开关 */
    private boolean lastEnchant = true;
    /** 脏标记：storage delta（能量/物理物品）变化时置位 */
    private volatile boolean dirty = false;
    /** storage delta 弱订阅句柄（惰性挂载） */
    private AutoCloseable deltaSub;

    /** 私有构造：仅允许通过 getOrCreate 创建 */
    private SwAmmoTracker(int netId) {
        this.netId = netId;
    }

    /**
     * 获取指定网络的追踪器（不存在则创建）
     */
    public static SwAmmoTracker getOrCreate(DimensionsNet net) {
        if (net == null) return null;
        return TRACKERS.computeIfAbsent(net.getId(), k -> new SwAmmoTracker(k));
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

    /**
     * 获取所属网络 ID
     */
    public int getNetId() {
        return netId;
    }

    /**
     * 标记数据已变化（下次 drain 输出增量）
     */
    public void markDirty() {
        dirty = true;
    }

    /** 惰性订阅：首次 drain 时经统一订阅中心挂 storage delta（能量/物理物品即时感知） */
    private synchronized void ensureSubscribed(DimensionsNet net) {
        if (deltaSub != null || net == null) return;
        deltaSub = BdSubscriptionHub.subscribe(net, this, (key, size, insert) -> {
            if (key instanceof ItemStackKey || key instanceof EnergyStackKey) {
                dirty = true;
            }
        });
    }

    /**
     * 差分产出增量数据。
     *
     * @return null 表示无变化；否则返回 DeltaResult（full=true 时为全量，否则为增量）
     */
    public DeltaResult drain(DimensionsNet net) {
        if (net == null) return null;
        if (!(net instanceof SuperbAmmoAccessor acc)) return null;
        ensureSubscribed(net);

        Map<String, Long> current = new HashMap<>(acc.getSuperbAmmo());
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();

        boolean metaChanged = !netName.equals(lastName == null ? "" : lastName) || enchantSep != lastEnchant;

        if (lastEnergy < 0 || metaChanged) {
            // 全量（首次或元数据变化）
            lastAmmo.clear();
            lastAmmo.putAll(current);
            lastEnergy = energy;
            lastName = netName;
            lastEnchant = enchantSep;
            dirty = false;
            return new DeltaResult(true, new HashMap<>(current), energy, netName, enchantSep);
        }

        if (!dirty && current.equals(lastAmmo) && energy == lastEnergy) {
            return null;
        }

        Map<String, Long> deltas = new HashMap<>();
        for (var entry : current.entrySet()) {
            Long old = lastAmmo.get(entry.getKey());
            long diff = old == null ? entry.getValue() : entry.getValue() - old;
            if (diff != 0) deltas.put(entry.getKey(), diff);
        }
        for (var entry : lastAmmo.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                deltas.put(entry.getKey(), -entry.getValue());
            }
        }

        lastAmmo.clear();
        lastAmmo.putAll(current);
        long energyDelta = energy - lastEnergy;
        lastEnergy = energy;
        lastName = netName;
        lastEnchant = enchantSep;
        dirty = false;

        return new DeltaResult(false, deltas, energyDelta, netName, enchantSep);
    }

    /** 强制下一次 drain 输出全量 */
    public void forceFull() {
        lastEnergy = -1;
    }

    /**
     * 差分结果记录：full=true 表示 ammo/energy 为全量快照，
     * 否则为相对上次快照的增量（delta）
     */
    public record DeltaResult(boolean full, Map<String, Long> ammo, long energy, String netName,
                              boolean enchantSeparation) {
    }
}
