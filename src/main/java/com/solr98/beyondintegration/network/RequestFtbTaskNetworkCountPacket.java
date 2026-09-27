package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * FTB 任务网络数量查询请求（C2S）：携带任务 ID，
 * 服务端返回该物品任务目标在主网络中的匹配数量（FtbTaskNetworkCountResponsePacket）。
 */
public record RequestFtbTaskNetworkCountPacket(long taskId) implements CustomPacketPayload {
    public static final Type<RequestFtbTaskNetworkCountPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":request_ftb_task_network_count"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFtbTaskNetworkCountPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, RequestFtbTaskNetworkCountPacket::taskId,
                    RequestFtbTaskNetworkCountPacket::new);

    public static void handle(final RequestFtbTaskNetworkCountPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // 登记/续订 tooltip 推送订阅（网络变化时主动推送最新数量）
            com.solr98.beyondintegration.feature.ftb.FtbTooltipPushService.onRequest(player, packet.taskId());
            var info = com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper
                    .taskNetworkInfo(player, packet.taskId());
            FtbTaskNetworkCountResponsePacket response = info == null
                    ? new FtbTaskNetworkCountResponsePacket(packet.taskId(), -1, -1, "")
                    : new FtbTaskNetworkCountResponsePacket(packet.taskId(), info.count(), info.netId(), info.netName());
            PacketHandler.sendToPlayer(player, response);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
