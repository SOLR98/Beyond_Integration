package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C：服务端向客户端推送 TACZ 弹药计数更新（键为弹药 ID）。
 * <p>{@code partial=true} 表示该快照只包含玩家背包内枪械所需的弹药 ID（按玩家裁剪），
 * 客户端按"合并 + 0 值删除"应用；{@code partial=false} 为整网络全量快照。
 */
public class TaczAmmoPushS2CPacket {

    /** 网络 ID */
    private final int netId;
    /** 网络自定义名称 */
    private final String netName;
    /** 弹药 ID -> 数量（0 表示已耗尽，客户端据此删除该条目） */
    private final Map<String, Integer> updates;
    /** 是否为按玩家背包裁剪的部分快照 */
    private final boolean partial;

    /** 构造推送包（默认按玩家裁剪的部分快照） */
    public TaczAmmoPushS2CPacket(int netId, String netName, Map<String, Integer> updates) {
        this(netId, netName, updates, true);
    }

    public TaczAmmoPushS2CPacket(int netId, String netName, Map<String, Integer> updates, boolean partial) {
        this.netId = netId;
        this.netName = netName;
        this.updates = updates;
        this.partial = partial;
    }

    /** 编码：写入网络 ID、名称、partial 标记及更新映射 */
    public static void encode(TaczAmmoPushS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeUtf(msg.netName);
        buf.writeBoolean(msg.partial);
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
        boolean partial = buf.readBoolean();
        int size = buf.readVarInt();
        Map<String, Integer> updates = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            updates.put(buf.readUtf(), buf.readVarInt());
        }
        return new TaczAmmoPushS2CPacket(netId, netName, updates, partial);
    }

    /** 客户端处理：将更新合并进 TaczAmmoCache */
    public static void handle(TaczAmmoPushS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                TaczAmmoCache.update(msg.netId, msg.netName, msg.updates, msg.partial);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
