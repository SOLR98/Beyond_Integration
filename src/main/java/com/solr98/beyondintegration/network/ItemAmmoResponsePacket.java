package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** ITEM 型弹药"现查现用"响应：netId + itemKey("ITEM:<注册名>") → 计数 */
/** 物品型弹药计数响应包（S2C）：服务端应答 RequestItemAmmoPacket，返回指定网络内各物品型弹药的当前数量，客户端写入 SuperbAmmoCache */
public class ItemAmmoResponsePacket {

    /** 网络节点 ID */
    private final int netId;
    /** 物品键 → 数量的映射 */
    private final Map<String, Long> counts;

    public ItemAmmoResponsePacket(int netId, Map<String, Long> counts) {
        this.netId = netId;
        this.counts = counts != null ? new HashMap<>(counts) : new HashMap<>();
    }

    /** 写入 netId 与计数映射到缓冲区 */
    public static void encode(ItemAmmoResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.counts.size());
        for (var entry : msg.counts.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarLong(entry.getValue());
        }
    }

    /** 从缓冲区读取并还原数据包 */
    public static ItemAmmoResponsePacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        int size = buf.readVarInt();
        Map<String, Long> counts = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            counts.put(buf.readUtf(), buf.readVarLong());
        }
        return new ItemAmmoResponsePacket(netId, counts);
    }

    /** 客户端收到后用计数更新 SuperbAmmoCache */
    public static void handle(ItemAmmoResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.updateItemCounts(msg.netId, msg.counts);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
