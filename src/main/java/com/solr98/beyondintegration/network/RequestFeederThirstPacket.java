package com.solr98.beyondintegration.network;

import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：请求当前打开的网络喂食器阈值（打开界面时发一次）。
 * <p>设置存在喂食物品 NBT 上，客户端本地副本不可信，故由服务端回推权威值。
 */
public record RequestFeederThirstPacket() implements CustomPacketPayload {

    public static final Type<RequestFeederThirstPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_feeder_thirst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFeederThirstPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestFeederThirstPacket());

    public static void handle(final RequestFeederThirstPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof NetFeederMenu menu)) return;
            if (menu.menuStack == null || menu.menuStack.isEmpty()) return;
            PacketDistributor.sendToPlayer(player, FeederThirstStatePacket.of(menu.menuStack));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
