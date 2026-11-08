package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.network.PrimaryNetSyncManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端在检测到异常（协议/时序错位、状态缺失）时请求重新同步。
 * <p>服务端幂等处理：重建该玩家会话基线并全量重推，同时重推配置；双向限流。
 */
public final class SyncResyncRequestPacket {

    private final int reason;

    public SyncResyncRequestPacket(int reason) {
        this.reason = reason;
    }

    public static void encode(SyncResyncRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.reason);
    }

    public static SyncResyncRequestPacket decode(FriendlyByteBuf buf) {
        return new SyncResyncRequestPacket(buf.readVarInt());
    }

    public static void handle(SyncResyncRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                PrimaryNetSyncManager.requestResync(player, msg.reason);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
