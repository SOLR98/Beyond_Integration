package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestGlobalSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端：进入世界后等待一段固定时间，再请求首次全局同步（NeoForge 版）。
 *
 * <p>等待时长由客户端配置 {@code primary_net_sync_settle_ticks} 控制（默认 600 tick = 30 s）。
 * 到期后发 {@link RequestGlobalSyncPayload}；仍无数据则每 {@value #RETRY_INTERVAL} tick 重试，
 * 最多 {@value #MAX_RETRIES} 次；离开世界/断线重置。
 */
@EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class GlobalSyncRequester {

    private static final int DEFAULT_SETTLE_TICKS = 600;
    private static final int RETRY_INTERVAL = 200;
    private static final int MAX_RETRIES = 3;

    private static int worldTicks = 0;
    private static int retries = 0;
    private static int lastSendAt = Integer.MIN_VALUE / 2;

    private GlobalSyncRequester() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            reset();
            return;
        }
        worldTicks++;

        if (PrimaryNetClientStorage.INSTANCE.hasNetwork()) {
            return;
        }
        if (retries >= MAX_RETRIES) {
            return;
        }

        int settleTicks = settleTicks();
        if (worldTicks < settleTicks) {
            return;
        }
        if (retries > 0 && worldTicks - lastSendAt < RETRY_INTERVAL) {
            return;
        }

        PacketHandler.sendToServer(new RequestGlobalSyncPayload());
        retries++;
        lastSendAt = worldTicks;
    }

    private static int settleTicks() {
        try {
            return Math.max(0, ClientConfig.CLIENT.netSyncSettleTicks.get());
        } catch (Throwable t) {
            return DEFAULT_SETTLE_TICKS;
        }
    }

    private static void reset() {
        worldTicks = 0;
        retries = 0;
        lastSendAt = Integer.MIN_VALUE / 2;
    }
}
