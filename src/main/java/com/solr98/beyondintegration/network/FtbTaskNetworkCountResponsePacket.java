package com.solr98.beyondintegration.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * FTB 任务网络库存查询响应（S2C）：客户端写入 {@code FtbTaskNetworkCountCache}，
 * 供任务 tooltip 显示"{网络标识}网络：N"（count = -1 表示无网络/不可用，不显示）。
 */
public class FtbTaskNetworkCountResponsePacket {

    private final long taskId;
    private final long count;
    private final int netId;
    private final String netName;

    public FtbTaskNetworkCountResponsePacket(long taskId, long count, int netId, String netName) {
        this.taskId = taskId;
        this.count = count;
        this.netId = netId;
        this.netName = netName == null ? "" : netName;
    }

    public long taskId() { return taskId; }
    public long count() { return count; }
    public int netId() { return netId; }
    public String netName() { return netName; }

    public static void encode(FtbTaskNetworkCountResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.taskId);
        buf.writeVarLong(msg.count);
        buf.writeVarInt(msg.netId);
        buf.writeUtf(msg.netName);
    }

    public static FtbTaskNetworkCountResponsePacket decode(FriendlyByteBuf buf) {
        return new FtbTaskNetworkCountResponsePacket(buf.readVarLong(), buf.readVarLong(),
                buf.readVarInt(), buf.readUtf());
    }

    public static void handle(FtbTaskNetworkCountResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.solr98.beyondintegration.client.FtbTaskNetworkCountCache
                        .apply(msg.taskId, msg.count, msg.netId, msg.netName)));
        ctx.get().setPacketHandled(true);
    }
}
