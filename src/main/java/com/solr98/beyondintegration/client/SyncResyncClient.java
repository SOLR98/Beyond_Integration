package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SyncResyncRequestPacket;
import net.minecraft.client.Minecraft;

/**
 * 客户端异常重同步：检测到镜像与配置/预期不一致时，请求服务端重建基线并全量重推。
 * <p>本地冷却，避免请求风暴；服务端另有独立限流。
 */
public final class SyncResyncClient {

    public static final int UNKNOWN = 0;
    public static final int CONFIG_MISMATCH = 1;
    public static final int STATE_MISSING = 2;

    private static final long COOLDOWN = 100L;
    private static long lastRequestTick = Long.MIN_VALUE;

    private SyncResyncClient() {}

    /** 请求重新同步（本地冷却内忽略）。 */
    public static void request(int reason) {
        long now = clientTick();
        if (now != Long.MIN_VALUE && lastRequestTick != Long.MIN_VALUE && now - lastRequestTick < COOLDOWN) return;
        lastRequestTick = now == Long.MIN_VALUE ? 0L : now;
        com.solr98.beyondintegration.core.sync.NetSyncDebug.log("client request resync reason={}", reason);
        try {
            PacketHandler.sendToServer(new SyncResyncRequestPacket(reason));
        } catch (Throwable ignored) {
        }
    }

    public static void reset() {
        lastRequestTick = Long.MIN_VALUE;
    }

    private static long clientTick() {
        try {
            var level = Minecraft.getInstance().level;
            return level == null ? Long.MIN_VALUE : level.getGameTime();
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }
}
