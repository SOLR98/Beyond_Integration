package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestAmmoCountPacket;
import com.solr98.beyondintegration.client.mirror.SharedNetData;
import com.solr98.beyondintegration.core.sync.NetDataType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TACZ 弹药客户端缓存：缓存服务端推送的网络弹药计数。
 * <p>结构为「弹药 ID →（网络 ID → 条目）」，条目携带显式 {@code infinite} 标记，
 * 不再用 {@code Integer.MAX_VALUE} 哨兵参与求和；另维护「弹药 ID → 预聚合」，
 * 使 HUD 每帧读取保持在 O(1)。读操作按节流（10 tick）合并暂存更新。
 */
public class TaczAmmoCache {

    /** 单网络单弹药的缓存条目（infinite 显式化，避免 MAX_VALUE 参与运算） */
    private record AmmoEntry(int netId, String customName, long count, boolean infinite) {}

    /** 预聚合：HUD 每帧读取，必须 O(1) */
    private record Agg(long total, boolean infiniteAny) {}

    /** 弹药 ID →（网络 ID → 条目） */
    private static final Map<String, Map<Integer, AmmoEntry>> byAmmo = new ConcurrentHashMap<>();
    /** 弹药 ID → 预聚合 */
    private static final Map<String, Agg> agg = new ConcurrentHashMap<>();
    /** 网络 ID → 该网络持有的弹药 ID（单网络替换时避免全表扫描） */
    private static final Map<Integer, Set<String>> indexByNet = new ConcurrentHashMap<>();
    /** 全网络无限弹药标记（服务端 "*" 语义） */
    private static volatile boolean allInfinite = false;

    /** 是否已有有效数据 */
    private static volatile boolean hasData = false;
    /** 进行中的快捷请求弹药 ID（同 ID 去重，对齐 1.21.1） */
    private static volatile String pendingQuickId = null;
    /** 快捷请求发起时间（超时复位，防服务端无响应时永久卡死） */
    private static volatile long pendingQuickTime = 0L;
    /** 快捷请求超时（毫秒）：服务端无响应（如玩家无网络）时允许重新请求 */
    private static final long QUICK_TIMEOUT_MS = 2000L;

    // ── 当前缓存所属网络 ──
    private static volatile int netId = -1;
    private static volatile String netName = "";

    // ── 节流暂存（收到更新先暂存，满 10 tick 且读取时再应用）──
    private static volatile Map<String, Integer> pending = null;
    private static volatile int pendingNetId = -1;
    private static volatile String pendingNetName = "";
    /** 本次暂存是否为部分快照（只含请求的弹药 ID，应用时合并而非整网络替换） */
    private static volatile boolean pendingPartial = false;
    private static volatile boolean hasPending = false;
    private static volatile int lastAppliedTick = -1;
    private static final int APPLY_INTERVAL_TICKS = 10;

    // ── 背包预取（只预取玩家背包里枪械所需的弹药 ID）──
    private static volatile int lastPrefetchTick = -1;
    private static final int PREFETCH_INTERVAL_TICKS = 40;

    /** 该弹药是否有缓存数据（先应用到期暂存） */
    public static boolean hasData(ResourceLocation ammoId) {
        if (mirrorAvailable()) return true;
        applyIfDue();
        if (!hasData) return false;
        return allInfinite || agg.containsKey(ammoId.toString());
    }

    /** 主网络镜像是否覆盖 TACZ 弹药（ITEM 类型；条件式取数）。 */
    private static boolean mirrorAvailable() {
        // TUD 的弹药判定数据由双端各自从本地 config 目录加载（非网络同步），
        // 客户端与服务端可能不一致；为保证与权威一致，TUD 加载时回退服务端推送通道。
        // 例外：服务端已下发 TUD 映射表（TudMappingClient.synced()）时，客户端可复现解析，镜像恢复可用。
        if (com.solr98.beyondintegration.compat.tud.TudAmmoCompat.isLoaded()
                && !com.solr98.beyondintegration.client.compat.TudMappingClient.synced()) return false;
        return SharedNetData.available(NetDataType.ITEM);
    }

    /** 获取指定弹药缓存数量（无限弹药返回 Integer.MAX_VALUE，无数据返回 0） */
    public static int getCount(ResourceLocation ammoId) {
        if (mirrorAvailable()) {
            // 镜像可用：物理弹药由 TaczAmmoMirror 按镜像版本聚合，无需服务端推送/请求；
            // 仅把推送通道里的无限标记（创造箱）合并进来，避免 EXT 段未同步时低估。
            int mirror = TaczAmmoMirror.getCount(ammoId);
            if (mirror == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            applyIfDue();
            if (allInfinite) return Integer.MAX_VALUE;
            Agg a = agg.get(ammoId.toString());
            if (a != null && a.infiniteAny()) return Integer.MAX_VALUE;
            return mirror;
        }
        prefetchInventory();
        applyIfDue();
        if (!hasData) return 0;        if (allInfinite) return Integer.MAX_VALUE;
        Agg a = agg.get(ammoId.toString());
        if (a == null) return 0;
        if (a.infiniteAny()) return Integer.MAX_VALUE;
        return (int) Math.min(a.total(), Integer.MAX_VALUE);
    }

    /** 获取该弹药所在网络（网络ID→数量，供提取/详情显示用） */
    public static Map<Integer, Integer> getAllNetworks(ResourceLocation ammoId) {
        applyIfDue();
        if (!hasData) return Collections.emptyMap();
        Map<Integer, AmmoEntry> nets = byAmmo.get(ammoId.toString());
        if (nets == null || nets.isEmpty()) return Collections.emptyMap();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        nets.forEach((id, e) -> result.put(id,
                e.infinite() ? Integer.MAX_VALUE : (int) Math.min(e.count(), Integer.MAX_VALUE)));
        return result;
    }

    /** 获取缓存所属网络 ID */
    public static int getNetId() {
        if (mirrorAvailable()) return PrimaryNetClientStorage.INSTANCE.netId();
        applyIfDue();
        return netId;
    }

    /** 获取缓存所属网络名称 */
    public static String getNetName() {
        if (mirrorAvailable()) return PrimaryNetClientStorage.INSTANCE.getNetworkName();
        applyIfDue();
        return netName;
    }

    /** 快速请求弹药数据（同 ID 去重 + 超时复位：服务端无响应时不会永久卡死） */
    public static void requestQuick(ResourceLocation ammoId) {
        // 镜像可用时弹药由客户端本地聚合，不需要向服务端请求
        if (mirrorAvailable()) return;
        String id = ammoId.toString();
        long now = System.currentTimeMillis();
        if (id.equals(pendingQuickId) && now - pendingQuickTime < QUICK_TIMEOUT_MS) return;
        pendingQuickId = id;
        pendingQuickTime = now;
        PacketHandler.sendToServer(new RequestAmmoCountPacket());
    }

    /** 收到服务端网络弹药快照（全量推送/全量应答）：仅暂存，由 applyIfDue 按节流应用 */
    public static void update(int netId, String netName, Map<String, Integer> ammoMap) {
        update(netId, netName, ammoMap, false);
    }

    /**
     * 收到服务端网络弹药数据：仅暂存，由 applyIfDue 按节流应用。
     *
     * @param partial true = 只含请求的弹药 ID（预取应答），应用时合并、不删除该网络其他条目
     */
    public static void update(int netId, String netName, Map<String, Integer> ammoMap, boolean partial) {
        pending = new HashMap<>(ammoMap);
        pendingNetId = netId;
        pendingNetName = netName != null ? netName : "";
        pendingPartial = partial;
        hasPending = true;
        applyIfDue();
    }

    /**
     * 背包预取：只对玩家背包里枪械所需的弹药 ID 发起请求（去重、节流 40 tick）。
     * <p>镜像可用时不需要预取（弹药由本地镜像聚合）；HUD 读取时顺带触发。
     */
    public static void prefetchInventory() {
        if (mirrorAvailable()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int tick = mc.player.tickCount;
        if (lastPrefetchTick >= 0 && tick - lastPrefetchTick < PREFETCH_INTERVAL_TICKS) return;
        lastPrefetchTick = tick;

        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (com.tacz.guns.api.item.IGun.getIGunOrNull(stack) == null) continue;
            ResourceLocation ammoId = com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor.getAmmoIdClient(stack);
            if (ammoId != null) ids.add(ammoId);
        }
        if (ids.isEmpty()) return;
        PacketHandler.sendToServer(new RequestAmmoCountPacket(new java.util.ArrayList<>(ids)));
    }

    /** 暂存数据达到节流间隔（10 tick）或无玩家对象时立即应用 */
    private static void applyIfDue() {
        if (!hasPending) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            applyPending();
            return;
        }
        int tick = mc.player.tickCount;
        if (lastAppliedTick >= 0 && tick - lastAppliedTick < APPLY_INTERVAL_TICKS) return;
        applyPending();
    }

    /**
     * 应用暂存数据（推送/响应均为「单网络全量快照」）：
     * - 网络切换时直接清空旧缓存（不保留旧网络数据）；
     * - 同网络时只移除该网络的旧条目（不扫全表），再写入本次条目；
     * - 只对受影响的弹药 ID 重算预聚合。
     */
    private static void applyPending() {
        int incomingNet = pendingNetId;
        String incomingName = pendingNetName;
        Map<String, Integer> snapshot = pending;

        // netId < 0：服务端通知"无权限/请丢弃"，清空全部缓存
        if (incomingNet < 0) {
            byAmmo.clear();
            indexByNet.clear();
            agg.clear();
            allInfinite = false;
            hasData = false;
            netId = -1;
            netName = "";
            pendingQuickId = null;
            pendingQuickTime = 0L;
            pending = null;
            pendingPartial = false;
            hasPending = false;
            return;
        }

        Set<String> affected = new java.util.HashSet<>();
        boolean partial = pendingPartial;
        if (incomingNet != netId) {
            byAmmo.clear();
            indexByNet.clear();
            agg.clear();
            allInfinite = false;
        } else if (!partial) {
            Set<String> owned = indexByNet.remove(incomingNet);
            if (owned != null) {
                for (String id : owned) {
                    Map<Integer, AmmoEntry> nets = byAmmo.get(id);
                    if (nets != null && nets.remove(incomingNet) != null) {
                        if (nets.isEmpty()) byAmmo.remove(id);
                        affected.add(id);
                    }
                }
            }
        }

        if (snapshot != null) {
            for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
                String id = e.getKey();
                Integer value = e.getValue();
                if ("*".equals(id)) {
                    allInfinite = true;
                    continue;
                }
                long count = value == null ? 0L : value;
                boolean infinite = count == Integer.MAX_VALUE;
                if (!infinite && count <= 0L) {
                    Map<Integer, AmmoEntry> nets = byAmmo.get(id);
                    if (nets != null && nets.remove(incomingNet) != null) {
                        if (nets.isEmpty()) byAmmo.remove(id);
                        affected.add(id);
                    }
                    continue;
                }
                byAmmo.computeIfAbsent(id, k -> new HashMap<>())
                        .put(incomingNet, new AmmoEntry(incomingNet, incomingName, count, infinite));
                indexByNet.computeIfAbsent(incomingNet, k -> new java.util.HashSet<>()).add(id);
                affected.add(id);
            }
        }

        for (String id : affected) {
            recomputeAgg(id);
        }

        netId = incomingNet;
        netName = incomingName;
        hasData = true;
        pendingQuickId = null;
        pendingQuickTime = 0L;
        lastAppliedTick = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.tickCount : 0;
        pending = null;
        hasPending = false;
    }

    /** 重算单个弹药 ID 的预聚合（无条目则移除） */
    private static void recomputeAgg(String ammoId) {
        Map<Integer, AmmoEntry> nets = byAmmo.get(ammoId);
        if (nets == null || nets.isEmpty()) {
            agg.remove(ammoId);
            return;
        }
        long total = 0L;
        boolean infiniteAny = false;
        for (AmmoEntry e : nets.values()) {
            if (e.infinite()) {
                infiniteAny = true;
            } else {
                total += e.count();
            }
        }
        agg.put(ammoId, new Agg(total, infiniteAny));
    }

    /** 清空全部缓存与状态（登出/进服时调用） */
    public static void clear() {
        byAmmo.clear();
        agg.clear();
        indexByNet.clear();
        allInfinite = false;
        hasData = false;
        pendingQuickId = null;
        pendingQuickTime = 0L;
        netId = -1;
        netName = "";
        pending = null;
        pendingPartial = false;
        hasPending = false;
        lastAppliedTick = -1;
        lastPrefetchTick = -1;
    }
}
