package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 弹药数量响应包（S2C）：服务端应答客户端的弹药查询请求，将网络节点各类弹药的计数与自定义名称同步到客户端缓存 */
public class AmmoCountResponsePacket {

    /** 网络节点 ID */
    private final int netId;
    /** 网络节点的自定义名称 */
    private final String netName;
    /** 弹药注册名 → 数量的映射 */
    private final Map<String, Integer> ammoMap;

    public AmmoCountResponsePacket(int netId, String netName, Map<String, Integer> ammoMap) {
        this.netId = netId;
        this.netName = netName;
        this.ammoMap = ammoMap;
    }

    /** 将数据包内容写入网络缓冲区（netId、netName、弹药计数映射） */
    public static void encode(AmmoCountResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeUtf(msg.netName);
        buf.writeVarInt(msg.ammoMap.size());
        for (var entry : msg.ammoMap.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    /** 从网络缓冲区读取并还原数据包 */
    public static AmmoCountResponsePacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        String netName = buf.readUtf();
        int size = buf.readVarInt();
        Map<String, Integer> ammoMap = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ammoMap.put(buf.readUtf(), buf.readVarInt());
        }
        return new AmmoCountResponsePacket(netId, netName, ammoMap);
    }

    /** 客户端收到后更新 TaczAmmoCache 缓存 */
    public static void handle(AmmoCountResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                TaczAmmoCache.update(msg.netId, msg.netName, msg.ammoMap);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
