package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 工作台状态客户端缓存（当前界面快照）。
 * 服务端通过 WorkstationActivationSyncPacket 下发献祭激活开关、已激活列表与服务端启用列表；
 * BD 终端界面打开时请求一次，激活成功后服务端主动推送更新。
 */
public final class WorkstationActivationCache {

    /** 献祭激活开关（false 时不做锁定显示） */
    private static volatile boolean enabled = false;
    /** 当前网络已激活的工作台 ID 集合 */
    private static volatile Set<String> activated = Set.of();
    /** 服务端启用的工作台 ID 集合（保序，workstationsSynced 为 true 时权威） */
    private static volatile Set<String> enabledWorkstations = Set.of();
    /** 是否已收到服务端启用列表（未收到时回退本地 common 配置） */
    private static volatile boolean workstationsSynced = false;

    private WorkstationActivationCache() {}

    /** S2C 写入：更新开关、已激活集合与服务端启用列表 */
    public static void update(boolean enabledIn, List<String> ids, List<String> enabledWorkstationsIn) {
        enabled = enabledIn;
        activated = ids == null ? Set.of() : Set.copyOf(ids);
        LinkedHashSet<String> ws = new LinkedHashSet<>();
        if (enabledWorkstationsIn != null) {
            for (String s : enabledWorkstationsIn) {
                if (s != null && !s.isEmpty()) ws.add(s.toLowerCase(Locale.ROOT));
            }
        }
        enabledWorkstations = Collections.unmodifiableSet(ws);
        workstationsSynced = true;
    }

    /** 断开连接/切换界面时重置 */
    public static void reset() {
        enabled = false;
        activated = Set.of();
        enabledWorkstations = Set.of();
        workstationsSynced = false;
    }

    /** 该工作台是否处于"未激活锁定"状态（开关关闭或非可激活工作台时恒 false） */
    public static boolean isLocked(String id) {
        return enabled && WorkstationActivation.isActivatable(id)
                && !activated.contains(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    /** 服务端是否启用了该工作台（未同步时回退本地 common 配置） */
    public static boolean isWorkstationEnabled(String id) {
        if (id == null || id.isEmpty()) return false;
        if (workstationsSynced) return enabledWorkstations.contains(id.toLowerCase(Locale.ROOT));
        return CommandConfig.isWorkstationEnabled(id);
    }

    /** 是否已收到服务端可用工作台列表 */
    public static boolean isWorkstationsSynced() {
        return workstationsSynced;
    }

    /** 服务端下发的可用工作台 ID 列表（保序；未同步时为空列表） */
    public static List<String> syncedWorkstations() {
        return workstationsSynced ? List.copyOf(enabledWorkstations) : List.of();
    }
}
