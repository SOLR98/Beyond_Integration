package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * FTB 任务网络库存查询响应（S2C）：客户端写入 {@code FtbTaskNetworkCountCache}，
 * 供任务 tooltip 显示"{网络标识}网络：N"（count = -1 表示无网络/不可用，不显示）。
 */
public record FtbTaskNetworkCountResponsePacket(long taskId, long count, int netId, String netName)
        implements CustomPacketPayload {
    public static final Type<FtbTaskNetworkCountResponsePacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":ftb_task_network_count"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FtbTaskNetworkCountResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, FtbTaskNetworkCountResponsePacket::taskId,
                    ByteBufCodecs.VAR_LONG, FtbTaskNetworkCountResponsePacket::count,
                    ByteBufCodecs.VAR_INT, FtbTaskNetworkCountResponsePacket::netId,
                    ByteBufCodecs.STRING_UTF8, FtbTaskNetworkCountResponsePacket::netName,
                    FtbTaskNetworkCountResponsePacket::new);

    public static void handle(final FtbTaskNetworkCountResponsePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> com.solr98.beyondintegration.client.FtbTaskNetworkCountCache
                .apply(packet.taskId(), packet.count(), packet.netId(), packet.netName()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
