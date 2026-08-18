package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端请求切换"主网络"的附魔分离开关，
 * 服务端校验玩家为网络的经理/所有者后取反开关，并回发同步包。
 */
public class ToggleEnchantSeparationPacket {

    /** 空构造：本包无字段 */
    public ToggleEnchantSeparationPacket() {}

    /** 编码：无字段，为空操作 */
    public static void encode(ToggleEnchantSeparationPacket msg, FriendlyByteBuf buf) {}

    /** 解码：无字段，直接返回新实例 */
    public static ToggleEnchantSeparationPacket decode(FriendlyByteBuf buf) {
        return new ToggleEnchantSeparationPacket();
    }

    /** 服务端处理：权限校验通过后取反开关状态、标记网络已修改并回发 EnchantSeparationSyncPacket */
    public static void handle(ToggleEnchantSeparationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.cannot_toggle_enchant_sep"));
                return;
            }
            if (net instanceof EnchantSeparationAccessor ea) {
                ea.beyond$setEnchantSeparationEnabled(!ea.beyond$isEnchantSeparationEnabled());
                net.setDirty();
                PacketHandler.sendToPlayer(player, new EnchantSeparationSyncPacket(ea.beyond$isEnchantSeparationEnabled()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
