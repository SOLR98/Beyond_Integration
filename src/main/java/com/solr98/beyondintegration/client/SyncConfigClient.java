package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope;

/**
 * 客户端「主网络同步」运行时配置缓存（由 {@code SyncConfigSyncPacket} 更新）。
 * <p>以服务端推送的权威值为准，供 UI / 诊断使用；可用性判断应以镜像实际类型位为准。
 */
public final class SyncConfigClient {

    private static volatile boolean enabled = false;
    private static volatile PrimaryNetSyncScope scope = PrimaryNetSyncScope.ITEMS;

    private SyncConfigClient() {}

    public static void set(boolean e, PrimaryNetSyncScope s) {
        enabled = e;
        scope = s == null ? PrimaryNetSyncScope.ITEMS : s;
        com.solr98.beyondintegration.core.sync.NetSyncDebug.log("client config: enabled={} scope={}", enabled, scope);
    }

    public static boolean isActive() {
        return enabled;
    }

    public static PrimaryNetSyncScope scope() {
        return scope;
    }

    public static void reset() {
        enabled = false;
        scope = PrimaryNetSyncScope.ITEMS;
    }
}
