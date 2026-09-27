package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestFtbTaskNetworkCountPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FTB 任务网络库存客户端缓存：任务 tooltip 悬停时按需查询主网络的库存数量与来源网络。
 * <p>
 * 带请求去重（2 秒超时）与结果 TTL（5 秒），避免 tooltip 每帧渲染时重复发包；
 * 服务端响应由 {@code FtbTaskNetworkCountResponsePacket} 写入（count = -1 视为无数据）。
 * tooltip 显示格式："{网络标识}网络：N"（标识优先网络自定义名，无则网络 ID）。
 */
public final class FtbTaskNetworkCountCache {

    /** 任务网络信息：库存数量 + 来源网络（ID / 展示标识） */
    public record Entry(long count, int netId, String netName) {}

    /** 任务 ID → 网络库存信息 */
    private static final Map<Long, Entry> COUNTS = new ConcurrentHashMap<>();
    /** 任务 ID → 最近一次响应时间戳（ms） */
    private static final Map<Long, Long> STAMPS = new ConcurrentHashMap<>();
    /** 任务 ID → 请求发出时间戳（ms），用于去重 */
    private static final Map<Long, Long> PENDING = new ConcurrentHashMap<>();

    /** 结果有效期（ms），过期后返回旧值并异步刷新 */
    private static final long TTL_MS = 5000L;
    /** 请求去重窗口（ms） */
    private static final long PENDING_TIMEOUT_MS = 2000L;

    private FtbTaskNetworkCountCache() {}

    /**
     * 获取任务的网络库存信息（无缓存返回 null 并触发异步查询；过期时返回旧值并触发刷新）。
     */
    public static Entry get(long taskId) {
        long now = System.currentTimeMillis();
        Entry entry = COUNTS.get(taskId);
        Long stamp = STAMPS.get(taskId);
        if (entry == null || stamp == null || now - stamp >= TTL_MS) {
            request(taskId, now);
        }
        return entry;
    }

    /** 应用服务端响应（count < 0 视为无数据，清除缓存） */
    public static void apply(long taskId, long count, int netId, String netName) {
        PENDING.remove(taskId);
        if (count < 0) {
            COUNTS.remove(taskId);
            STAMPS.remove(taskId);
            return;
        }
        COUNTS.put(taskId, new Entry(count, netId, netName == null ? "" : netName));
        STAMPS.put(taskId, System.currentTimeMillis());
    }

    /** 清理全部缓存（进服/登出） */
    public static void clear() {
        COUNTS.clear();
        STAMPS.clear();
        PENDING.clear();
    }

    /** 发送查询请求（去重窗口内不重复发送） */
    private static void request(long taskId, long now) {
        Long pending = PENDING.get(taskId);
        if (pending != null && now - pending < PENDING_TIMEOUT_MS) return;
        PENDING.put(taskId, now);
        PacketHandler.sendToServer(new RequestFtbTaskNetworkCountPacket(taskId));
    }
}
