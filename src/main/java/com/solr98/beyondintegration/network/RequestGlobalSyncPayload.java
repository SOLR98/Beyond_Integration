package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：客户端「进入世界且稳定」后请求首次全局同步（NeoForge payload 版）。
 *
 * <p>与 1.20.1 分支的 {@code RequestGlobalSyncPacket} 语义一致：
 * 首同步由客户端驱动，服务端收到后才开始 bind + 流式全量；客户端不发时服务端有 60 s 兜底。
 */
public record RequestGlobalSyncPayload() implements CustomPacketPayload {

    public static final Type<RequestGlobalSyncPayload> TYPE =
            new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":request_global_sync"));

    public static final StreamCodec<FriendlyByteBuf, RequestGlobalSyncPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestGlobalSyncPayload());

    public static void handle(final RequestGlobalSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                PrimaryNetSyncManager.onClientReady(sp);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
