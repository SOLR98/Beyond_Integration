package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.DimensionsAnvilMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端设置维度铁砧界面中的物品名称，
 * 服务端校验容器 ID 后写入对应 DimensionsAnvilMenu 的改名状态。
 */
public record SetAnvilNamePacket(int containerId, String name) {

    /** 编码：写入容器 ID 与名称（最长 50 字符） */
    public static void encode(SetAnvilNamePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeUtf(msg.name, 50);
    }

    /** 解码：读出容器 ID 与名称 */
    public static SetAnvilNamePacket decode(FriendlyByteBuf buf) {
        return new SetAnvilNamePacket(buf.readVarInt(), buf.readUtf(50));
    }

    /** 服务端处理：若玩家打开的容器是对应的铁砧菜单则应用改名 */
    public static void handle(SetAnvilNamePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof DimensionsAnvilMenu menu && menu.containerId == msg.containerId) {
                menu.rename(msg.name);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
