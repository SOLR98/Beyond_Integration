package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.mirror.SharedNetData;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.solr98.beyondintegration.network.AmmoCountResponsePacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestAmmoCountPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * TACZ 弹药数量客户端缓存（静态工具类）。
 * <p>缓存「弹药 ID →（网络 ID → 条目）」，另维护「弹药 ID → 预聚合」，
 * 使 HUD 每帧读取为 O(1)；无限弹药以 {@code infinite} 语义表达
 * （协议侧沿用 {@code Integer.MAX_VALUE} 表示无限，落地时转为标记）。
 * 服务端响应先暂存，按 10 tick 节流统一应用到缓存。
 */
public class TaczAmmoCache {

    /** 预聚合：各网络求和 + 是否存在无限 */
    private record Agg(long total, boolean infiniteAny) {}

    private static final Map<String, Map<Integer, AmmoCountResponsePacket.NetEntry>> cache = new HashMap<>(); // 弹药 ID → 各网络计数条目
    private static final Map<String, Agg> agg = new HashMap<>(); // 弹药 ID → 预聚合
    private static final Set<String> responded = new HashSet<>(); // 已收到响应的弹药 ID 集合
    private static String pendingQuickId = null; // 进行中的快捷请求弹药 ID（去重）
    private static String pendingFullId = null; // 进行中的全量请求弹药 ID（去重）
    private static long lastFullUpdateMs = 0; // 上次全量请求时间
    private static final long FULL_INTERVAL_MS = 6000; // 全量请求最小间隔
    private static long pendingQuickTime = 0; // 快捷请求发起时间（超时复位，防服务端无响应时永久卡死）
    private static final long QUICK_TIMEOUT_MS = 2000; // 快捷请求超时（毫秒）

    // ── 节流暂存（收到更新先暂存，满 10 tick 且读取时再应用）──
    private static final Map<String, Map<Integer, AmmoCountResponsePacket.NetEntry>> pending = new HashMap<>();
    private static final Set<String> pendingResponded = new HashSet<>();
    private static volatile boolean hasPending = false;
    private static volatile int lastAppliedTick = -1;
    private static final int APPLY_INTERVAL_TICKS = 10;

    // ── 背包预取（只预取玩家背包里枪械所需的弹药 ID）──
    private static volatile int lastPrefetchTick = -1;
    private static final int PREFETCH_INTERVAL_TICKS = 40;

    /** 主网络镜像是否覆盖 TACZ 弹药（ITEM 类型；条件式取数）。镜像可用时无需服务端推送/请求。 */
    private static boolean mirrorAvailable() {
        return SharedNetData.available(NetDataType.ITEM);
    }

    public static boolean hasData(ResourceLocation ammoId) {
        if (mirrorAvailable()) return true;
        applyIfDue();
        return agg.containsKey(ammoId.toString());
    }

    /** 获取弹药总数量（各网络求和；存在无限时返回 {@code Integer.MAX_VALUE}） */
    public static int getCount(ResourceLocation ammoId) {
        if (mirrorAvailable()) {
            // 镜像可用：物理弹药由 TaczAmmoMirror 按镜像版本聚合；
            // 仅把推送通道里的无限标记合并进来，避免 EXT 段未同步时低估。
            int mirror = TaczAmmoMirror.getCount(ammoId);
            if (mirror == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            applyIfDue();
            Agg a = agg.get(ammoId.toString());
            if (a != null && a.infiniteAny()) return Integer.MAX_VALUE;
            return mirror;
        }
        prefetchInventory();
        applyIfDue();
        Agg a = agg.get(ammoId.toString());
        if (a == null) return 0;
        if (a.infiniteAny()) return Integer.MAX_VALUE;
        return (int) Math.min(a.total(), Integer.MAX_VALUE);
    }

    /** 获取各网络独立的弹药数量映射 */
    public static Map<Integer, Integer> getAllNetworkCounts(ResourceLocation ammoId) {
        if (mirrorAvailable()) {
            int id = PrimaryNetClientStorage.INSTANCE.netId();
            int count = TaczAmmoMirror.getCount(ammoId);
            if (id < 0 || count <= 0) return Collections.emptyMap();
            return Collections.singletonMap(id, count);
        }
        applyIfDue();
        Map<Integer, AmmoCountResponsePacket.NetEntry> nets = cache.get(ammoId.toString());
        if (nets == null) return Collections.emptyMap();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        nets.forEach((id, e) -> result.put(id, e.count()));
        return result;
    }

    /** 获取网络显示名（自定义名优先，其次翻译键，兜底 Net#id） */
    public static Component getNetworkDisplayName(int netId, ResourceLocation ammoId) {
        applyIfDue();
        Map<Integer, AmmoCountResponsePacket.NetEntry> nets = cache.get(ammoId.toString());
        if (nets == null) return Component.literal("Net#" + netId);
        AmmoCountResponsePacket.NetEntry entry = nets.get(netId);
        if (entry == null) return Component.literal("Net#" + netId);
        String name = entry.customName();
        if (name != null && !name.isEmpty())
            return Component.literal(name + " (Net#" + netId + ")");
        return Component.translatable("menu.text.beyonddimensions.net.default_name", netId);
    }

    /** 快捷请求弹药计数（同 ID 去重 + 超时复位；镜像可用时不请求） */
    public static void requestQuick(ResourceLocation ammoId) {
        if (mirrorAvailable()) return;
        String id = ammoId.toString();
        long now = System.currentTimeMillis();
        if (id.equals(pendingQuickId) && now - pendingQuickTime < QUICK_TIMEOUT_MS) return;
        pendingQuickId = id;
        pendingQuickTime = now;
        PacketHandler.sendToServer(new RequestAmmoCountPacket(ammoId, true));
    }

    /** 全量请求弹药计数（带 6 秒最小间隔；镜像可用时不请求） */
    public static void requestFull(ResourceLocation ammoId) {
        if (mirrorAvailable()) return;
        long now = System.currentTimeMillis();
        if (now - lastFullUpdateMs < FULL_INTERVAL_MS) return;
        String id = ammoId.toString();
        if (id.equals(pendingFullId)) return;
        pendingFullId = id;
        lastFullUpdateMs = now;
        PacketHandler.sendToServer(new RequestAmmoCountPacket(ammoId, false));
    }

    /**
     * 背包预取：只对玩家背包里枪械所需的弹药 ID 发起请求（去重、节流 40 tick）。
     * <p>镜像可用时不需要预取（弹药由本地镜像聚合）。
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
        for (ResourceLocation id : ids) {
            requestQuick(id);
        }
    }

    /** 请求响应（ammoId 级别整组覆盖）：仅暂存，由 applyIfDue 按节流应用 */
    public static void update(ResourceLocation ammoId, Map<Integer, AmmoCountResponsePacket.NetEntry> networks, boolean quick) {
        pending.put(ammoId.toString(), new LinkedHashMap<>(networks));
        if (quick) pendingQuickId = null;
        else pendingFullId = null;
        pendingQuickTime = 0;
        pendingResponded.add(ammoId.toString());
        hasPending = true;
        applyIfDue();
    }

    /** 服务端轮询推送（netId 级别：该网络条目整体替换为推送列表） */
    /** 全量快照写入（某网络的所有条目整体覆盖该网络的条目；netId<0 表示丢弃全部缓存） */
    public static void applyPush(int netId, String netName, Map<String, Integer> ammo) {
        if (netId < 0) {
            clear();
            return;
        }
        replaceNetEntries(pending, netId, netName, ammo);
        for (String id : ammo.keySet()) {
            pendingResponded.add(id);
        }
        hasPending = true;
        applyIfDue();
    }

    /** 用指定网络的新条目替换目标缓存中该网络的所有旧条目 */
    private static void replaceNetEntries(Map<String, Map<Integer, AmmoCountResponsePacket.NetEntry>> target,
                                          int netId, String netName, Map<String, Integer> ammo) {
        target.values().forEach(m -> m.entrySet().removeIf(e -> e.getKey() == netId));
        for (var entry : ammo.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>())
                    .put(netId, new AmmoCountResponsePacket.NetEntry(netId, netName, entry.getValue()));
        }
    }

    /** 若存在暂存且距上次应用满 10 tick（或未在游戏中），则应用暂存数据 */
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

    /** 将暂存数据整体合并入正式缓存并清空暂存区（只对受影响的弹药 ID 重算聚合） */
    private static void applyPending() {
        for (var entry : pending.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
            recomputeAgg(entry.getKey());
        }
        responded.addAll(pendingResponded);
        pending.clear();
        pendingResponded.clear();
        hasPending = false;
        lastAppliedTick = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.tickCount : 0;
    }

    /** 重算单个弹药 ID 的预聚合（无条目则移除） */
    private static void recomputeAgg(String ammoId) {
        Map<Integer, AmmoCountResponsePacket.NetEntry> nets = cache.get(ammoId);
        if (nets == null || nets.isEmpty()) {
            agg.remove(ammoId);
            return;
        }
        long total = 0L;
        boolean infiniteAny = false;
        for (AmmoCountResponsePacket.NetEntry e : nets.values()) {
            if (e.count() == Integer.MAX_VALUE) {
                infiniteAny = true;
            } else {
                total += e.count();
            }
        }
        agg.put(ammoId, new Agg(total, infiniteAny));
    }

    /** 清空全部缓存与请求状态 */
    public static void clear() {
        cache.clear();
        agg.clear();
        responded.clear();
        pendingQuickId = null;
        pendingFullId = null;
        lastFullUpdateMs = 0;
        pendingQuickTime = 0;
        pending.clear();
        pendingResponded.clear();
        hasPending = false;
        lastAppliedTick = -1;
        lastPrefetchTick = -1;
    }
}
