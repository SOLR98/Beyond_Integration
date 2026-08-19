package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/** 弹药数量查询请求包（C2S）：客户端进入 TACZ 相关界面时请求服务端汇总主网络的弹药计数，服务端以 AmmoCountResponsePacket 应答 */
public class RequestAmmoCountPacket {

    /** 空参构造：该请求包不携带数据 */
    public RequestAmmoCountPacket() {}

    /** 无数据可写 */
    public static void encode(RequestAmmoCountPacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static RequestAmmoCountPacket decode(FriendlyByteBuf buf) {
        return new RequestAmmoCountPacket();
    }

    /** 服务端执行：从主网络统计弹药计数与网络名，打包 AmmoCountResponsePacket 回发给玩家 */
    public static void handle(RequestAmmoCountPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary == null) return;

            Map<String, Integer> ammoMap = TaczAmmoExtractor.countAllAmmoInNetwork(primary);
            String netName = primary instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketHandler.sendToPlayer(player, new AmmoCountResponsePacket(primary.getId(), netName, ammoMap));
        });
        ctx.get().setPacketHandled(true);
    }
}
