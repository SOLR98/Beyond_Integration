package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.network.PrimaryNetSyncManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端「已进入世界且界面稳定」后请求首次全局同步。
 *
 * <p>背景：BD 的同步是菜单会话式的；BI 的全局镜像由服务端主动推。大整合包载入世界时
 * 客户端最忙，若此时立刻推全量，会占满 netty/主线程导致 KeepAlive 回包延迟。
 *
 * <p>因此首同步改为**客户端驱动**：客户端在世界稳定（进入世界后经过一段时间、无界面遮挡）
 * 才发本包；服务端收到后才开始 bind + 全量流式推送。客户端未发时服务端保留 30 s 兜底，
 * 兼容旧客户端。
 */
public class RequestGlobalSyncPacket {

    public static void encode(RequestGlobalSyncPacket msg, FriendlyByteBuf buf) {
        // 空包
    }

    public static RequestGlobalSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestGlobalSyncPacket();
    }

    public static void handle(RequestGlobalSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                PrimaryNetSyncManager.onClientReady(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
