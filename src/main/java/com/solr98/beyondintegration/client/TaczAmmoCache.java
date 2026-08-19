package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestAmmoCountPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TACZ 模组弹药客户端缓存：缓存服务端推送的全网络弹药计数，
 * 读操作按节流（10 tick）合并暂存更新，并支持按需请求全量数据。
 */
public class TaczAmmoCache {

    /** 弹药 ID → 数量 缓存（"*" 表示全网络无限弹药） */
    private static final Map<String, Integer> cache = new ConcurrentHashMap<>();
    /** 是否已有有效数据 */
    private static volatile boolean hasData = false;
    /** 是否已有待响应请求（用于去重） */
    private static volatile boolean requestPending = false;
    /** 最近一次请求时间戳（超时复位，防止服务端无响应时永久卡死） */
    private static volatile long requestTime = 0L;
    /** 请求超时时间（毫秒）：服务端无响应（如玩家无网络）时允许重新请求 */
    private static final long REQUEST_TIMEOUT_MS = 2000L;

    // ── 当前缓存所属网络 ──
    private static int netId = -1;
    private static String netName = "";

    // ── 节流暂存（收到更新先暂存，满 10 tick 且读取时再应用）──
    private static volatile Map<String, Integer> pending = null;
    private static volatile int pendingNetId = -1;
    private static volatile String pendingNetName = "";
    private static volatile boolean hasPending = false;
    private static volatile int lastAppliedTick = -1;
    private static final int APPLY_INTERVAL_TICKS = 10;

    /** 该弹药是否有缓存数据（先应用到期暂存） */
    public static boolean hasData(ResourceLocation ammoId) {
        applyIfDue();
        return hasData;
    }

    /** 获取指定弹药缓存数量（无限弹药返回 Integer.MAX_VALUE，无数据返回 0） */
    public static int getCount(ResourceLocation ammoId) {
        applyIfDue();
        if (!hasData) return 0;
        Integer allCreative = cache.get("*");
        if (allCreative != null && allCreative == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return cache.getOrDefault(ammoId.toString(), 0);
    }

    /** 获取该弹药所在网络（数量>0 时返回 网络ID→数量，供提取用） */
    public static Map<Integer, Integer> getAllNetworks(ResourceLocation ammoId) {
        applyIfDue();
        if (!hasData) return Collections.emptyMap();
        int count = getCount(ammoId);
        if (count > 0) {
            return Collections.singletonMap(netId, count);
        }
        return Collections.emptyMap();
    }

    /** 获取缓存所属网络 ID */
    public static int getNetId() {
        applyIfDue();
        return netId;
    }

    /** 获取缓存所属网络名称 */
    public static String getNetName() {
        applyIfDue();
        return netName;
    }

    /** 快速请求全量弹药数据（去重 + 超时复位：服务端无响应时不会永久卡死） */
    public static void requestQuick(ResourceLocation ammoId) {
        long now = System.currentTimeMillis();
        if (requestPending && now - requestTime < REQUEST_TIMEOUT_MS) return;
        requestPending = true;
        requestTime = now;
        PacketHandler.sendToServer(new RequestAmmoCountPacket());
    }

    /** 收到服务端全量列表（推送或请求响应）：仅暂存，由 applyIfDue 按节流应用 */
    public static void update(int netId, String netName, Map<String, Integer> ammoMap) {
        pending = new HashMap<>(ammoMap);
        pendingNetId = netId;
        pendingNetName = netName != null ? netName : "";
        hasPending = true;
        applyIfDue();
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
     * 应用暂存数据（推送/响应均为全量快照）：
     * - 网络切换时直接清空旧缓存（不保留旧网络数据）；
     * - 同网络时先移除快照中已消失的键（弹药拿空后旧键不残留），再合并更新。
     */
    private static void applyPending() {
        if (pendingNetId != netId) {
            cache.clear();
        } else {
            cache.keySet().retainAll(pending.keySet());
        }
        cache.putAll(pending);
        netId = pendingNetId;
        netName = pendingNetName;
        hasData = true;
        requestPending = false;
        requestTime = 0L;
        lastAppliedTick = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.tickCount : 0;
        pending = null;
        hasPending = false;
    }

    /** 清空全部缓存与状态（登出/进服时调用） */
    public static void clear() {
        cache.clear();
        hasData = false;
        requestPending = false;
        requestTime = 0L;
        netId = -1;
        netName = "";
        pending = null;
        hasPending = false;
        lastAppliedTick = -1;
    }
}
