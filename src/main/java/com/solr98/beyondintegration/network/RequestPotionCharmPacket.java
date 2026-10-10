package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.PotionCharmAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 网络药水护符开关状态查询请求包（C2S）：服务端读取当前网络开关状态并以 PotionCharmSyncPacket 应答 */
public class RequestPotionCharmPacket {

    /** 空参构造：该请求包不携带数据 */
    public RequestPotionCharmPacket() {}

    /** 无数据可写 */
    public static void encode(RequestPotionCharmPacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static RequestPotionCharmPacket decode(FriendlyByteBuf buf) {
        return new RequestPotionCharmPacket();
    }

    /** 服务端执行：读取网络开关状态并回发 PotionCharmSyncPacket */
    public static void handle(RequestPotionCharmPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;
            int mode = net instanceof PotionCharmAccessor acc ? acc.beyond$getPotionCharmMode() : 0;
            boolean mendingUnlocked = NetworkAmmoData.getOrCreate(net.getId()).isPotionCharmMending();
            PacketHandler.sendToPlayer(player, new PotionCharmSyncPacket(mode, mendingUnlocked));
        });
        ctx.get().setPacketHandled(true);
    }
}
