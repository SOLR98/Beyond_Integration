package com.solr98.beyondintegration.network;

import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求当前打开的经验棒界面的自动修复设置（打开界面时发一次）。
 * <p>设置存在经验棒物品 NBT 上，客户端本地副本不可信，故由服务端回推权威值。
 */
public class RequestXpRepairPacket {

    public RequestXpRepairPacket() {
    }

    public static void encode(RequestXpRepairPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestXpRepairPacket decode(FriendlyByteBuf buf) {
        return new RequestXpRepairPacket();
    }

    public static void handle(RequestXpRepairPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof XpExchangeMenu menu)) return;
            if (menu.menuStack == null || menu.menuStack.isEmpty()) return;
            XpRepairStatePacket.sendTo(player, menu.menuStack);
        });
        ctx.get().setPacketHandled(true);
    }
}
