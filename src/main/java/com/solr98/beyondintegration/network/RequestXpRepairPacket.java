package com.solr98.beyondintegration.network;

import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：请求当前打开的经验棒界面的自动修复设置（打开界面时发一次）。
 * <p>设置存在经验棒物品 NBT 上，客户端本地副本不可信，故由服务端回推权威值。
 */
public record RequestXpRepairPacket() implements CustomPacketPayload {

    public static final Type<RequestXpRepairPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_xp_repair"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestXpRepairPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestXpRepairPacket());

    public static void handle(final RequestXpRepairPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof XpExchangeMenu menu)) return;
            if (menu.menuStack == null || menu.menuStack.isEmpty()) return;
            PacketDistributor.sendToPlayer(player, XpRepairStatePacket.of(menu.menuStack));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
