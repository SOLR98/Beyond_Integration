package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.solr98.beyondintegration.feature.soul.SoulEnergyStackKey;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SoulEnergySyncPacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端：把玩家主网络的灵魂量同步给客户端（供 HUD 青色「网络魂段」），
 * 并在变化时触发 Goety 自身 SE 同步（由 {@code SEUpdatePacketMixin} 把网络魂并入原版条）。
 * <p><b>同步机制</b>：改为 <b>BD 存储订阅驱动</b>，替代原「每 20t 全量轮询推送」：
 * <ul>
 *   <li>每个在线玩家订阅其主网络存储 delta（{@link BdSubscriptionHub}），
 *       仅当 {@code soul_energy} 键发生增删时把该玩家标记为脏；</li>
 *   <li>玩家 tick 仅做轻量检测（主网络切换 / 激活状态变化），
 *       仅在「网络魂量或来源状态真正变化」时才发送一个包，空闲时零流量；</li>
 *   <li>登出 / 换网 / 网络销毁 / 服务器停止时关闭订阅。</li>
 * </ul>
 * <p>仅 1.20.1，且仅在 {@code goety} 加载时注册（类体引用 Goety SEHelper）。
 */
public class SoulHudSyncHandler {

    /** 单玩家会话：绑定的主网络 + 订阅句柄 + 上次推送基线。 */
    private static final class Session {
        int netId = -1;
        AutoCloseable subscription;
        /** 订阅回调置真：本 tick 需要重新读取网络魂量。 */
        volatile boolean dirty = true;
        int lastAmount = 0;
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
        tick(player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        close(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (Session session : sessions.values()) session.close();
        sessions.clear();
    }

    /** 网络销毁：关闭并移除绑定该网络的会话。 */
    @SubscribeEvent
    public void onNetDestroyed(com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) {
        int netId = event.getDestroyedId();
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().netId == netId) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    /** 单玩家同步：解析主网络、维护会话，仅在状态变化时推送。 */
    private void tick(ServerPlayer player) {
        UUID id = player.getUUID();

        if (!CommandConfig.soulEnabled()) {
            Session session = sessions.get(id);
            if (session != null) {
                if (session.lastSource) push(player, 0, false);
                close(id);
            }
            return;
        }

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        Session session = sessions.get(id);

        if (session == null) {
            if (net != null && NetworkSoulSource.usable(net)) bind(player, net);
            return;
        }

        int netId = net == null ? -1 : net.getId();
        if (session.netId != netId) {
            boolean hadSource = session.lastSource;
            session.close();
            sessions.remove(id);
            if (hadSource) push(player, 0, false);
            if (net != null && NetworkSoulSource.usable(net)) bind(player, net);
            return;
        }

        boolean usable = NetworkSoulSource.usable(net);
        int amount = session.lastAmount;
        if (usable && (session.dirty || usable != session.lastSource)) {
            amount = SoulEnergyAccess.getSoulsInt(net);
        }
        if (usable != session.lastSource || amount != session.lastAmount) {
            int pushAmount = usable ? amount : 0;
            push(player, pushAmount, usable);
            if (usable) com.Polarice3.Goety.utils.SEHelper.sendSEUpdatePacket(player);
            session.lastAmount = pushAmount;
            session.lastSource = usable;
        }
        session.dirty = false;
    }

    /** 绑定（或重绑）玩家主网络：订阅 soul_energy delta 并立即推送一次当前状态。 */
    private void bind(ServerPlayer player, DimensionsNet net) {
        Session session = new Session();
        session.netId = net.getId();
        // 订阅方以自身（session）为 owner 捕获 dirty 标记；会话关闭 / 网络销毁时由
        // BdSubscriptionHub 关闭句柄释放强引用（与 PrimaryNetSyncManager 用法一致）。
        session.subscription = BdSubscriptionHub.subscribe(net, session, (key, size, insert) -> {
            if (key instanceof SoulEnergyStackKey) session.dirty = true;
        });

        int amount = SoulEnergyAccess.getSoulsInt(net);
        sessions.put(player.getUUID(), session);
        push(player, amount, true);
        com.Polarice3.Goety.utils.SEHelper.sendSEUpdatePacket(player);
        session.lastAmount = amount;
        session.lastSource = true;
        session.dirty = false;
    }

    private void push(ServerPlayer player, int amount, boolean source) {
        PacketHandler.sendToPlayer(player, new SoulEnergySyncPacket(amount, source));
    }

    private void close(UUID id) {
        Session session = sessions.remove(id);
        if (session != null) session.close();
    }
}
