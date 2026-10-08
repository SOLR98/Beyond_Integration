package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 会话级「主网络物品计数」同步管理器（服务端）。
 * <p>
 * 玩家入服（且服务端配置开启）后订阅其主网络（{@link DimensionsNet#getPrimaryNetFromPlayer}）的物品存储：
 * 立即发一次全量，之后逐 tick 合并增量发送；主网络切换时重订阅并重发全量；退出时解订阅。
 * 配置关闭时不订阅，并发送一次清空信号，使客户端回退到“仅 BD 终端界面”行为。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PrimaryNetSyncManager {

    /** 单包最大条目数（超出分包） */
    private static final int MAX_BATCH = 512;
    /** 登录后到首次同步的延迟（tick），等待玩家/网络状态就绪 */
    private static final int INITIAL_DELAY_TICKS = 100;
    /** 主网络暂不可用时的重试间隔（tick） */
    private static final int RETRY_INTERVAL_TICKS = 40;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** 待首次同步的玩家 → 允许绑定的服务器 tick */
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private PrimaryNetSyncManager() {}

    private static final class Session {
        int netId = -1;
        AbstractUnorderedStackHandler storage;
        AutoCloseable anySub;
        AutoCloseable deltaSub;
        final Map<IStackKey<?>, Long> pending = new HashMap<>();
        final Map<IStackKey<?>, Long> lastSent = new HashMap<>();
        boolean dirtyFull = false;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CommandConfig.primaryNetJeiSync()) {
            sendClear(player);
            return;
        }
        // 登录后延迟一段时间再首次同步，等待玩家/网络状态就绪
        PENDING.put(player.getUUID(), tickNow(player) + INITIAL_DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player.getUUID());
            PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        tick(player);
    }

    private static long tickNow(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }

    private static void tick(ServerPlayer player) {
        boolean enabled = CommandConfig.primaryNetJeiSync();
        Session session = SESSIONS.get(player.getUUID());

        if (!enabled) {
            PENDING.remove(player.getUUID());
            if (session != null) {
                close(player.getUUID());
                sendClear(player);
            }
            return;
        }

        long now = tickNow(player);

        if (session == null) {
            Long deadline = PENDING.get(player.getUUID());
            if (deadline != null && now < deadline) return; // 登录后延迟中
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                PENDING.put(player.getUUID(), now + RETRY_INTERVAL_TICKS);
                return;
            }
            PENDING.remove(player.getUUID());
            bind(player, net);
            return;
        }

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        int netId = net == null ? -1 : net.getId();
        if (session.netId != netId) {
            close(player.getUUID());
            if (net == null) {
                sendClear(player);
                PENDING.put(player.getUUID(), now + RETRY_INTERVAL_TICKS);
            } else {
                bind(player, net);
            }
            return;
        }
        flush(player, session);
    }

    private static void bind(ServerPlayer player, DimensionsNet net) {
        Session session = new Session();
        session.netId = net.getId();
        session.storage = net.getUnifiedStorage();
        session.anySub = session.storage.subscribeAny(session, () -> session.dirtyFull = true);
        session.deltaSub = session.storage.subscribeDelta(session, (key, size, insert) -> {
            if (key instanceof ItemStackKey) {
                session.pending.put(key, session.storage.getStackByKey(key).amount());
            }
        });
        session.dirtyFull = true;
        SESSIONS.put(player.getUUID(), session);
    }

    private static void close(UUID id) {
        Session session = SESSIONS.remove(id);
        if (session == null) return;
        try {
            if (session.anySub != null) session.anySub.close();
        } catch (Throwable ignored) {}
        try {
            if (session.deltaSub != null) session.deltaSub.close();
        } catch (Throwable ignored) {}
    }

    private static void sendClear(ServerPlayer player) {
        PacketHandler.sendToPlayer(player,
                new PrimaryNetSyncPacket(true, false, -1, List.of(), List.of()));
    }

    /** 每 tick 合并 pending / 全量差异，分包发送并推进基线。 */
    private static void flush(ServerPlayer player, Session session) {
        if (!session.dirtyFull && session.pending.isEmpty()) return;

        Map<IStackKey<?>, Long> toSend = new LinkedHashMap<>();

        if (session.dirtyFull) {
            Map<IStackKey<?>, Long> now = new HashMap<>();
            for (KeyAmount ka : session.storage.getStorage()) {
                if (ka == null || ka.isEmpty() || !(ka.key() instanceof ItemStackKey)) continue;
                now.merge(ka.key(), ka.amount(), Long::sum);
            }
            Set<IStackKey<?>> all = new HashSet<>(session.lastSent.keySet());
            all.addAll(now.keySet());
            for (IStackKey<?> key : all) {
                long oldCount = session.lastSent.getOrDefault(key, 0L);
                long newCount = now.getOrDefault(key, 0L);
                if (newCount != oldCount) toSend.put(key, newCount);
            }
            session.pending.clear();
            session.dirtyFull = false;
        } else {
            toSend.putAll(session.pending);
            session.pending.clear();
        }

        if (toSend.isEmpty()) return;

        for (Map.Entry<IStackKey<?>, Long> e : toSend.entrySet()) {
            if (e.getValue() <= 0L) session.lastSent.remove(e.getKey());
            else session.lastSent.put(e.getKey(), e.getValue());
        }

        List<IStackKey<?>> batchKeys = new ArrayList<>();
        List<Long> batchCounts = new ArrayList<>();
        for (Map.Entry<IStackKey<?>, Long> e : toSend.entrySet()) {
            batchKeys.add(e.getKey());
            batchCounts.add(e.getValue());
            if (batchKeys.size() >= MAX_BATCH) {
                sendBatch(player, session, batchKeys, batchCounts);
                batchKeys = new ArrayList<>();
                batchCounts = new ArrayList<>();
            }
        }
        if (!batchKeys.isEmpty()) sendBatch(player, session, batchKeys, batchCounts);
    }

    private static void sendBatch(ServerPlayer player, Session session,
                                  List<IStackKey<?>> keys, List<Long> counts) {
        PacketHandler.sendToPlayer(player,
                new PrimaryNetSyncPacket(false, true, session.netId, keys, counts));
    }
}
