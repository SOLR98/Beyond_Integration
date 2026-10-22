package com.solr98.beyondintegration.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端在检测到异常（协议/时序错位、状态缺失）时请求重新同步。
 * <p>服务端幂等处理：重建该玩家会话基线并全量重推，同时重推配置；双向限流。
 */
public record SyncResyncRequestPayload(int reason) implements CustomPacketPayload {

    public static final Type<SyncResyncRequestPayload> TYPE =
            new Type<>(ResourceLocation.parse("beyond_integration:sync_resync_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResyncRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SyncResyncRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new SyncResyncRequestPayload(buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncResyncRequestPayload payload) {
            buf.writeVarInt(payload.reason);
        }
    };

    public static void handle(final SyncResyncRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                PrimaryNetSyncManager.requestResync(player, payload.reason);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
