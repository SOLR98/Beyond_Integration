package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestGlobalSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端：进入世界后等待一段固定时间，再请求首次全局同步。
 *
 * <p>等待时长由客户端配置 {@code primary_net_sync_settle_ticks} 控制（默认 600 tick = 30 s），
 * 目的是错开客户端载入世界/区块/资源的忙期。到期后发 {@link RequestGlobalSyncPacket}；
 * 若仍未拿到主网络数据，每 {@link #RETRY_INTERVAL} tick 重试一次，最多 {@link #MAX_RETRIES} 次。
 *
 * <p>不依赖 {@code mc.screen}、区块加载、载入遮罩等状态——只认“进入世界后的时长”，
 * 行为在任何整合包里都一致。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GlobalSyncRequester {

    /** 配置读取失败时的兜底（30 s） */
    private static final int DEFAULT_SETTLE_TICKS = 600;
    /** 无数据时的重试间隔（10 s） */
    private static final int RETRY_INTERVAL = 200;
    /** 最多重试次数 */
    private static final int MAX_RETRIES = 3;

    private static int worldTicks = 0;
    private static int retries = 0;
    private static int lastSendAt = Integer.MIN_VALUE / 2;

    private GlobalSyncRequester() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            reset();
            return;
        }
        worldTicks++;

        // 已有主网络数据：不再请求
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

        PacketHandler.sendToServer(new RequestGlobalSyncPacket());
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
