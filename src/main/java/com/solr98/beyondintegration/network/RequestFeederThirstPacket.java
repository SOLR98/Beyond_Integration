package com.solr98.beyondintegration.network;

import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求当前打开的网络喂食器的口渴设置（打开界面时发一次）。
 * <p>喂食器设置存在手持物品 NBT 上，客户端本地副本不可信，故由服务端回推权威值。
 */
public class RequestFeederThirstPacket {

    public RequestFeederThirstPacket() {
    }

    public static void encode(RequestFeederThirstPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestFeederThirstPacket decode(FriendlyByteBuf buf) {
        return new RequestFeederThirstPacket();
    }

    public static void handle(RequestFeederThirstPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof NetFeederMenu menu)) return;
            if (menu.menuStack == null || menu.menuStack.isEmpty()) return;
            FeederThirstStatePacket.sendTo(player, menu.menuStack);
        });
        ctx.get().setPacketHandled(true);
    }
}
