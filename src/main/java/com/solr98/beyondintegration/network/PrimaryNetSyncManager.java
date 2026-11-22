package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.solr98.beyondintegration.core.sync.NetSyncDebug;
import com.solr98.beyondintegration.handler.NetworkSyncConfigPusher;
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
    /** 每玩家每 tick 最多发出的批次数（削峰） */
    private static final int MAX_BATCHES_PER_TICK = 1;
    /** 服务端兜底：登录后若一直没收到客户端的 RequestGlobalSync，最多等 60 s（1200 tick）后自行开始 */
    private static final int FALLBACK_DELAY_TICKS = 1200;
    /** 收到客户端「已稳定」请求后，再等 1 s（20 tick）开始首次同步 */
    private static final int POST_READY_DELAY_TICKS = 20;
    /** 主网络暂不可用时的重试间隔（tick） */
    private static final int RETRY_INTERVAL_TICKS = 40;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** 待首次同步的玩家 → 允许绑定的服务器 tick */
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private PrimaryNetSyncManager() {}

    /** 一个待发批次：键 + 绝对数量 */
    private static final class Batch {
        final List<IStackKey<?>> keys;
        final List<Long> counts;

        Batch(List<IStackKey<?>> keys, List<Long> counts) {
            this.keys = keys;
            this.counts = counts;
        }
    }
    private static final class Session {
        int netId = -1;
        AbstractUnorderedStackHandler storage;
        AutoCloseable anySub;
        AutoCloseable deltaSub;
        final Map<IStackKey<?>, Long> pending = new HashMap<>();
        final Map<IStackKey<?>, Long> lastSent = new HashMap<>();
        boolean dirtyFull = false;
        /** 待发批次（按 tick 预算逐批发送） */
        final java.util.ArrayDeque<Batch> outbox = new java.util.ArrayDeque<>();
        // EXT（NetworkAmmoData）快照：无 storage delta 事件，靠 per-tick 比较
        Map<String, Long> lastExtAmmo = null;
        long lastExtFlags = 0L;
        String lastExtName = null;
        boolean extDirty = false;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CommandConfig.primaryNetSync()) {
            sendClear(player);
            return;
        }
        PENDING.put(player.getUUID(), tickNow(player) + FALLBACK_DELAY_TICKS);
    }
    /**
     * 客户端「已进入世界且界面稳定」：把首同步等待缩短到 POST_READY_DELAY_TICKS；
     * 若会话已存在（重连后再次请求），直接标记全量重推。
     */
    public static void onClientReady(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        // 反刷：登录最初 1 s 内不受理；同一玩家 10 s 内只受理一次首同步/重同步请求
        if (player.tickCount < 20) return;
        if (!RequestThrottle.allow(player, "global_sync", 200)) return;
        if (!CommandConfig.primaryNetSync()) {
            sendClear(player);
            return;
        }
        long now = player.getServer().getTickCount();
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.dirtyFull = true;
            return;
        }
        PENDING.put(player.getUUID(), now + POST_READY_DELAY_TICKS);
        NetSyncDebug.log("client ready player={} first sync at {}", player.getUUID(), now + POST_READY_DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player.getUUID());
            PENDING.remove(player.getUUID());
            RequestThrottle.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.connection == null) return; // 假玩家（无客户端连接）：跳过
        tick(player);
    }

    private static long tickNow(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }

    private static void tick(ServerPlayer player) {
        boolean enabled = CommandConfig.primaryNetSync();
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
        if (net != null && CommandConfig.primaryNetSyncScope().hasExt()) {
            detectExtChange(net, session);
        }
        flush(player, session);
    }

    /** EXT 变化检测：虚拟弹药 / 开关 / 网络名（无事件源，per-tick 快照比较）。 */
    private static void detectExtChange(DimensionsNet net, Session session) {
        Map<String, Long> ammo = readExtAmmo(net);
        long flags = readFlags(net);
        String name = readName(net);
        if (session.lastExtAmmo == null
                || !session.lastExtAmmo.equals(ammo)
                || session.lastExtFlags != flags
                || !java.util.Objects.equals(session.lastExtName, name)) {
            session.extDirty = true;
            NetSyncDebug.log("ext change net={} ammo={} flags={} name={}",
                    session.netId, ammo.size(), flags, name);
        }
    }

    private static Map<String, Long> readExtAmmo(DimensionsNet net) {
        Map<String, Long> map = new HashMap<>();
        if (net instanceof com.solr98.beyondintegration.handler.SuperbAmmoAccessor acc) {
            map.putAll(acc.getSuperbAmmo());
        }
        // TACZ 创造箱虚拟计数（前缀 tacz:，避免与 SW 键空间冲突）
        if (net instanceof com.solr98.beyondintegration.handler.TaczCreativeAccessor tacz) {
            for (Map.Entry<String, Integer> e : tacz.getTaczCreativeCounts().entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    map.put("tacz:" + e.getKey(), (long) e.getValue());
                }
            }
        }
        return map;
    }

    private static long readFlags(DimensionsNet net) {
        long flags = 0L;
        if (net instanceof com.solr98.beyondintegration.handler.EnchantSeparationAccessor ea
                && ea.beyond$isEnchantSeparationEnabled()) {
            flags |= 1L << com.solr98.beyondintegration.core.sync.NetFlag.ENCHANT_SEPARATION.ordinal();
        }
        if (net instanceof com.solr98.beyondintegration.handler.EnergyChargeAccessor ec
                && ec.beyond$isEnergyChargeEnabled()) {
            flags |= 1L << com.solr98.beyondintegration.core.sync.NetFlag.ENERGY_CHARGE.ordinal();
        }
        return flags;
    }

    private static String readName(DimensionsNet net) {
        return net instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp ? nnp.getCustomName() : "";
    }

    private static void bind(ServerPlayer player, DimensionsNet net) {
        Session session = new Session();
        session.netId = net.getId();
        session.storage = net.getUnifiedStorage();
        session.anySub = session.storage.subscribeAny(session, () -> session.dirtyFull = true);
        session.deltaSub = session.storage.subscribeDelta(session, (key, size, insert) -> {
            if (key != null) {
                session.pending.put(key, session.storage.getStackByKey(key).amount());
            }
        });
        session.dirtyFull = true;
        session.extDirty = CommandConfig.primaryNetSyncScope().hasExt();
        SESSIONS.put(player.getUUID(), session);
        NetSyncDebug.log("bind player={} net={} scope={}", player.getUUID(), session.netId, CommandConfig.primaryNetSyncScope());
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

    /** 每 tick 合并 pending / 全量差异 + EXT 快照，分包发送并推进基线。 */
    private static void flush(ServerPlayer player, Session session) {
        com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope scope =
                CommandConfig.primaryNetSyncScope();
        long t0 = NetSyncDebug.start();

        // 1) 队列空时，把本轮全量/增量整理成批次（不发送、不推进基线）
        if (session.outbox.isEmpty() && (session.dirtyFull || !session.pending.isEmpty())) {
            buildBatches(session, scope);
        }

        // 2) 按预算发送：每玩家每 tick 最多 MAX_BATCHES_PER_TICK 批
        int budget = MAX_BATCHES_PER_TICK;
        int sentBatches = 0;
        int sentKeys = 0;
        while (!session.outbox.isEmpty() && budget-- > 0) {
            Batch batch = session.outbox.poll();
            for (int i = 0; i < batch.keys.size(); i++) {
                IStackKey<?> key = batch.keys.get(i);
                long count = batch.counts.get(i);
                if (count <= 0L) session.lastSent.remove(key);
                else session.lastSent.put(key, count);
            }
            sendBatch(player, session, batch.keys, batch.counts);
            sentBatches++;
            sentKeys += batch.keys.size();
        }

        // 3) EXT（虚拟弹药/开关/名称）：小包，逐 tick 推
        if (session.extDirty && scope.hasExt()) {
            DimensionsNet net = DimensionsNet.getNetFromId(session.netId);
            if (net != null) {
                Map<String, Long> ammo = readExtAmmo(net);
                long flags = readFlags(net);
                String name = readName(net);
                PacketHandler.sendToPlayer(player, new PrimaryNetSyncPacket(
                        false, true, session.netId, scope.mask(), List.of(), List.of(), ammo, name, flags));
                session.lastExtAmmo = ammo;
                session.lastExtFlags = flags;
                session.lastExtName = name;
            }
            session.extDirty = false;
        }

        if (sentBatches > 0) {
            NetSyncDebug.perf("flush", t0, "net", session.netId, "batches", sentBatches,
                    "keys", sentKeys, "queued", session.outbox.size());
        }
    }

    /** 把 pending / 全量差异整理成出站批次；只在 outbox 为空时调用。 */
    private static void buildBatches(Session session, com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope scope) {
        Map<IStackKey<?>, Long> toSend = new LinkedHashMap<>();
        if (session.dirtyFull) {
            Map<IStackKey<?>, Long> now = new HashMap<>();
            for (KeyAmount ka : session.storage.getStorage()) {
                if (ka == null || ka.isEmpty()) continue;
                IStackKey<?> key = ka.key();
                if (!scope.covers(key)) continue;
                now.merge(key, ka.amount(), Long::sum);
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
            for (Map.Entry<IStackKey<?>, Long> e : session.pending.entrySet()) {
                if (scope.covers(e.getKey())) toSend.put(e.getKey(), e.getValue());
            }
            session.pending.clear();
        }
        if (toSend.isEmpty()) return;

        List<IStackKey<?>> batchKeys = new ArrayList<>();
        List<Long> batchCounts = new ArrayList<>();
        for (Map.Entry<IStackKey<?>, Long> e : toSend.entrySet()) {
            batchKeys.add(e.getKey());
            batchCounts.add(e.getValue());
            if (batchKeys.size() >= MAX_BATCH) {
                session.outbox.add(new Batch(batchKeys, batchCounts));
                batchKeys = new ArrayList<>();
                batchCounts = new ArrayList<>();
            }
        }
        if (!batchKeys.isEmpty()) session.outbox.add(new Batch(batchKeys, batchCounts));
    }
    private static void sendBatch(ServerPlayer player, Session session,
                                  List<IStackKey<?>> keys, List<Long> counts) {
        int mask = CommandConfig.primaryNetSyncScope().mask();
        PacketHandler.sendToPlayer(player,
                new PrimaryNetSyncPacket(false, true, session.netId, mask, keys, counts));
    }

    // ───────────────── 异常重同步 / 配置变更 ─────────────────

    /** 最近一次 resync 的 tick（服务端防刷） */
    private static final Map<UUID, Long> LAST_RESYNC = new HashMap<>();
    private static final long RESYNC_COOLDOWN = 100L;

    /** 客户端请求重新同步：幂等重置该玩家会话基线，全量重推；顺带重推配置。 */
    public static void requestResync(ServerPlayer player, int reason) {
        if (player == null || player.getServer() == null) return;
        long now = player.getServer().getTickCount();
        Long last = LAST_RESYNC.get(player.getUUID());
        if (last != null && now - last < RESYNC_COOLDOWN) {
            NetSyncDebug.log("resync throttled player={} reason={}", player.getUUID(), reason);
            return;
        }
        LAST_RESYNC.put(player.getUUID(), now);
        NetSyncDebug.log("resync player={} reason={}", player.getUUID(), reason);

        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.dirtyFull = true;
            session.pending.clear();
            session.lastSent.clear();
            session.lastExtAmmo = null;
            session.extDirty = CommandConfig.primaryNetSyncScope().hasExt();
        } else {
            PENDING.remove(player.getUUID());
        }
        NetworkSyncConfigPusher.push(player);
    }

    /** 配置变更（范围 / 开关）：重置全部会话基线，下一 tick 按新范围全量重推。 */
    public static void onConfigChanged() {
        NetSyncDebug.log("config changed: reset {} sessions scope={}", SESSIONS.size(), CommandConfig.primaryNetSyncScope());
        for (Session session : SESSIONS.values()) {
            session.dirtyFull = true;
            session.pending.clear();
            session.lastSent.clear();
            session.lastExtAmmo = null;
            session.extDirty = CommandConfig.primaryNetSyncScope().hasExt();
        }
    }

    /** 服务器停止：清空全部会话与限流状态。 */
    public static void clear() {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            close(id);
        }
        SESSIONS.clear();
        PENDING.clear();
        LAST_RESYNC.clear();
        RequestThrottle.clearAll();
    }
}
