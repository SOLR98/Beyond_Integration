package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C：服务端向客户端推送 TACZ 弹药物品计数更新（键为 "ITEM:<注册名>"），
 * 客户端将其合并进 TaczAmmoCache 缓存。
 */
public class TaczAmmoPushS2CPacket {

    /** 网络 ID */
    private final int netId;
    /** 网络自定义名称 */
    private final String netName;
    /** 弹药键 -> 数量的增量更新映射 */
    private final Map<String, Integer> updates;

    /** 构造推送包 */
    public TaczAmmoPushS2CPacket(int netId, String netName, Map<String, Integer> updates) {
        this.netId = netId;
        this.netName = netName;
        this.updates = updates;
    }

    /** 编码：写入网络 ID、名称及更新映射 */
    public static void encode(TaczAmmoPushS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeUtf(msg.netName);
        buf.writeVarInt(msg.updates.size());
        for (var entry : msg.updates.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    /** 解码：按编码顺序还原全部字段 */
    public static TaczAmmoPushS2CPacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        String netName = buf.readUtf();
        int size = buf.readVarInt();
        Map<String, Integer> updates = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            updates.put(buf.readUtf(), buf.readVarInt());
        }
        return new TaczAmmoPushS2CPacket(netId, netName, updates);
    }

    /** 客户端处理：将更新合并进 TaczAmmoCache */
    public static void handle(TaczAmmoPushS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                TaczAmmoCache.update(msg.netId, msg.netName, msg.updates);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
