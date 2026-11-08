package com.solr98.beyondintegration.core.sync;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import org.slf4j.Logger;

/**
 * 主网络同步调试日志 + 性能探针。
 * <p>由 {@code primary_net_sync_debug} 控制；关闭时 {@link #start()} 返回 0、{@link #perf} 无输出，
 * 基本零开销（仅一次配置读取的 try 判断）。
 * <p>性能探针：{@code start()} 取纳秒，{@link #perf} 输出耗时；耗时 ≥ {@link #SLOW_NANOS} 时升级为 WARN。
 */
public final class NetSyncDebug {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 慢探针阈值（纳秒）：5ms。 */
    private static final long SLOW_NANOS = 5_000_000L;

    private NetSyncDebug() {}

    /** 调试开关（读配置；异常时视为关闭）。 */
    public static boolean on() {
        try {
            return CommandConfig.primaryNetSyncDebug();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 一般事件日志（仅开启时输出）。 */
    public static void log(String msg, Object... args) {
        if (on()) LOGGER.info("[BI-NetSync] " + msg, args);
    }

    /** 告警（无论开关；用于异常/慢探针）。 */
    public static void warn(String msg, Object... args) {
        LOGGER.warn("[BI-NetSync] " + msg, args);
    }

    /** 开始计时；关闭时返回 0（配合 {@link #perf}）。 */
    public static long start() {
        return on() ? System.nanoTime() : 0L;
    }

    /**
     * 结束计时并输出探针。
     *
     * @param tag        探针名
     * @param startNanos {@link #start()} 返回值；0 表示未计时（跳过）
     * @param detail     键值对（key1, val1, key2, val2, ...）
     */
    public static void perf(String tag, long startNanos, Object... detail) {
        if (!on() || startNanos == 0L) return;
        long nanos = System.nanoTime() - startNanos;
        long us = nanos / 1000L;
        if (nanos >= SLOW_NANOS) {
            LOGGER.warn("[BI-NetSync][SLOW] {} {}us {}", tag, us, fmt(detail));
        } else {
            LOGGER.info("[BI-NetSync] {} {}us {}", tag, us, fmt(detail));
        }
    }

    private static String fmt(Object... detail) {
        if (detail == null || detail.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < detail.length; i += 2) {
            sb.append(detail[i]).append('=').append(detail[i + 1]).append(' ');
        }
        return sb.toString().trim();
    }
}
