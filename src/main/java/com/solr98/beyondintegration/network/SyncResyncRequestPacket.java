package com.solr98.beyondintegration.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：客户端在检测到异常（协议/时序错位、状态缺失）时请求重新同步。
 * <p>服务端幂等处理：重建该玩家会话基线并全量重推，同时重推配置；双向限流。
 */
public record SyncResyncRequestPacket(int reason) implements CustomPacketPayload {

    public static final Type<SyncResyncRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "sync_resync_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResyncRequestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncResyncRequestPacket::reason,
                    SyncResyncRequestPacket::new);

    public static void handle(final SyncResyncRequestPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PrimaryNetSyncManager.requestResync(player, packet.reason());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
