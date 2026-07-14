package com.solr98.beyondintegration.feature.bind;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 公开 API，供 AE2/RS 附属模组查询审计数据。
 * 附属模组通过此 API 获取累计表和事件日志，自行实现展示层。
 */
public class BindingAuditAPI {

    // ========== NetInterface 累计取出量 ==========

    public static Map<ResourceLocation, Long> getInterfaceExtracts(int netId, BlockPos pos) {
        return NetworkMeters.getInterfaceTotals(netId, pos);
    }

    public static long getInterfaceTotalOps(int netId, BlockPos pos) {
        NetworkMeters.NetworkMeterGroup g = NetworkMeters.getNetworkMeters(netId);
        if (g == null) return 0;
        NetworkMeters.InterfaceMeter m = g.interfaces.get(pos);
        return m != null ? m.totalOps.get() : 0;
    }

    // ========== AE2 存储盘累计流量 ==========

    public static Map<ResourceLocation, Long> getCellInserted(int netId, UUID cellId) {
        NetworkMeters.CellMeter m = NetworkMeters.getCellFlow(netId, cellId);
        return m != null ? Map.copyOf(m.inserted) : Map.of();
    }

    public static Map<ResourceLocation, Long> getCellExtracted(int netId, UUID cellId) {
        NetworkMeters.CellMeter m = NetworkMeters.getCellFlow(netId, cellId);
        return m != null ? Map.copyOf(m.extracted) : Map.of();
    }

    // ========== RS 通道累计流量 ==========

    public static Map<ResourceLocation, Long> getPathwayInserted(int netId, BlockPos pos) {
        NetworkMeters.PathwayMeter m = NetworkMeters.getPathwayFlow(netId, pos);
        return m != null ? Map.copyOf(m.inserted) : Map.of();
    }

    public static Map<ResourceLocation, Long> getPathwayExtracted(int netId, BlockPos pos) {
        NetworkMeters.PathwayMeter m = NetworkMeters.getPathwayFlow(netId, pos);
        return m != null ? Map.copyOf(m.extracted) : Map.of();
    }

    // ========== 网络总览 ==========

    public static NetworkMeters.NetworkMeterGroup getNetworkMeters(int netId) {
        return NetworkMeters.getNetworkMeters(netId);
    }

    // ========== 事件日志查询 ==========

    public static List<AuditEntry> queryLog(int netId, int page, int pageSize) {
        BindingAuditLog log = BindingAuditLog.getInstance();
        if (log == null) return List.of();
        return log.queryByNet(netId, page, pageSize);
    }

    public static List<AuditEntry> queryLogByPlayer(String playerName, int page, int pageSize) {
        BindingAuditLog log = BindingAuditLog.getInstance();
        if (log == null) return List.of();
        return log.queryByPlayer(playerName, page, pageSize);
    }

    // ========== 令牌校验 ==========

    public static boolean isTokenValid(int netId, UUID token) {
        return BindingTokenManager.isTokenValid(netId, token);
    }

    public static UUID getCurrentToken(int netId, UUID playerUuid) {
        return BindingTokenManager.getOrCreateToken(netId, playerUuid);
    }
}
