package com.solr98.beyondintegration.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * FTB 任务网络数量查询请求（C2S）：携带任务 ID，
 * 服务端返回该物品任务目标在主网络中的匹配数量（FtbTaskNetworkCountResponsePacket）。
 */
public class RequestFtbTaskNetworkCountPacket {

    private final long taskId;

    public RequestFtbTaskNetworkCountPacket(long taskId) {
        this.taskId = taskId;
    }

    public long taskId() { return taskId; }

    public static void encode(RequestFtbTaskNetworkCountPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.taskId);
    }

    public static RequestFtbTaskNetworkCountPacket decode(FriendlyByteBuf buf) {
        return new RequestFtbTaskNetworkCountPacket(buf.readVarLong());
    }

    public static void handle(RequestFtbTaskNetworkCountPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // 登记/续订 tooltip 推送订阅（网络变化时主动推送最新数量）
            com.solr98.beyondintegration.feature.ftb.FtbTooltipPushService.onRequest(player, msg.taskId());
            var info = com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper
                    .taskNetworkInfo(player, msg.taskId());
            FtbTaskNetworkCountResponsePacket response = info == null
                    ? new FtbTaskNetworkCountResponsePacket(msg.taskId(), -1, -1, "")
                    : new FtbTaskNetworkCountResponsePacket(msg.taskId(), info.count(), info.netId(), info.netName());
            PacketHandler.sendToPlayer(player, response);
        });
        ctx.get().setPacketHandled(true);
    }
}
