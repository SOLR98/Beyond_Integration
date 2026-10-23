package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.network.ManaSyncPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端：把玩家主网络的网络魔力（{@link ManaStackKey}）同步给客户端（HUD 用）。
 * <p>订阅网络 {@code mana} 键 delta，仅在量/上限/来源变化时推送；空闲零流量。
 */
public class ManaHudSyncHandler {

    private static final class Session {
        int netId = -1;
        AutoCloseable subscription;
        volatile boolean dirty = true;
        long lastAmount = 0L;
        long lastCap = 0L;
        boolean lastSource = false;

        void close() {
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception ignored) {
                }
                subscription = null;
            }
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.connection == null) return; // 假玩家
        tick(player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        close(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (Session s : sessions.values()) s.close();
        sessions.clear();
    }

    @SubscribeEvent
    public void onNetDestroyed(DimensionsNetEvent.Destroyed event) {
        int netId = event.getDestroyedId();
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().netId == netId) {
                e.getValue().close();
                return true;
            }
            return false;
        });
    }

    private void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!CommandConfig.ironsManaEnabled()) {
            Session s = sessions.get(id);
            if (s != null) {
                if (s.lastSource) push(player, 0L, 0L, false);
                close(id);
            }
            return;
        }

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        Session session = sessions.get(id);
        if (session == null) {
            if (net != null) bind(player, net);
            return;
        }

        int netId = net == null ? -1 : net.getId();
        if (session.netId != netId) {
            boolean had = session.lastSource;
            session.close();
            sessions.remove(id);
            if (had) push(player, 0L, 0L, false);
            if (net != null) bind(player, net);
            return;
        }

        boolean usable = net != null;
        long amount = session.lastAmount;
        long cap = session.lastCap;
        if (usable && session.dirty) {
            amount = ManaAccess.getMana(net);
            cap = CommandConfig.ironsManaMax();
        }
        if (usable != session.lastSource || amount != session.lastAmount || cap != session.lastCap) {
            long pushAmount = usable ? amount : 0L;
            push(player, pushAmount, cap, usable);
            session.lastAmount = pushAmount;
            session.lastCap = cap;
            session.lastSource = usable;
        }
        session.dirty = false;
    }

    private void bind(ServerPlayer player, DimensionsNet net) {
        Session session = new Session();
        session.netId = net.getId();
        session.subscription = BdSubscriptionHub.subscribe(net, session, (key, size, insert) -> {
            if (key instanceof ManaStackKey) session.dirty = true;
        });
        long amount = ManaAccess.getMana(net);
        long cap = CommandConfig.ironsManaMax();
        sessions.put(player.getUUID(), session);
        push(player, amount, cap, true);
        session.lastAmount = amount;
        session.lastCap = cap;
        session.lastSource = true;
        session.dirty = false;
    }

    private void push(ServerPlayer player, long amount, long cap, boolean source) {
        PacketHandler.sendToPlayer(player, new ManaSyncPacket(amount, cap, source));
    }

    private void close(UUID id) {
        Session s = sessions.remove(id);
        if (s != null) s.close();
    }
}
