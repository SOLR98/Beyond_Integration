package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.NetworkItemCache;
import com.solr98.beyondintegration.client.CraftToast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 网络物品计数同步包（S2C）：服务端将网络的物品计数同步到客户端缓存，并可选携带合成结果以显示提示 */
public class NetworkItemCountsPacket {

    /** 物品键 → 数量的映射 */
    private final Map<String, Long> counts;
    /** true→全量替换缓存，false→增量合并 */
    private final boolean replace;
    /** 是否已连接网络（false 时清空客户端缓存） */
    private final boolean hasNetwork;
    /** 网络节点 ID */
    private final int netId;
    /** 合成结果物品（可为空） */
    private final ItemStack resultItem;
    /** 合成结果数量 */
    private final int resultCount;

    public NetworkItemCountsPacket(Map<String, Long> counts) {
        this(counts, false, true, -1, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace) {
        this(counts, replace, true, -1, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork) {
        this(counts, replace, hasNetwork, -1, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork, int netId) {
        this(counts, replace, hasNetwork, netId, ItemStack.EMPTY, 0);
    }

    public NetworkItemCountsPacket(Map<String, Long> counts, boolean replace, boolean hasNetwork, int netId,
                                    ItemStack resultItem, int resultCount) {
        this.counts = counts;
        this.replace = replace;
        this.hasNetwork = hasNetwork;
        this.netId = netId;
        this.resultItem = resultItem;
        this.resultCount = resultCount;
    }

    /** 写入 replace、hasNetwork、netId、计数映射及可选的结果物品到缓冲区 */
    public static void encode(NetworkItemCountsPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.replace);
        buf.writeBoolean(msg.hasNetwork);
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.counts.size());
        for (var e : msg.counts.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarLong(e.getValue());
        }
        boolean hasResult = !msg.resultItem.isEmpty();
        buf.writeBoolean(hasResult);
        if (hasResult) {
            buf.writeItemStack(msg.resultItem, true);
            buf.writeVarInt(msg.resultCount);
        }
    }

    /** 从缓冲区读取并还原数据包 */
    public static NetworkItemCountsPacket decode(FriendlyByteBuf buf) {
        boolean replace = buf.readBoolean();
        boolean hasNetwork = buf.readBoolean();
        int netId = buf.readVarInt();
        int size = buf.readVarInt();
        Map<String, Long> counts = new HashMap<>();
        for (int i = 0; i < size; i++) {
            counts.put(buf.readUtf(), buf.readVarLong());
        }
        boolean hasResult = buf.readBoolean();
        ItemStack resultItem = hasResult ? buf.readItem() : ItemStack.EMPTY;
        int resultCount = hasResult ? buf.readVarInt() : 0;
        return new NetworkItemCountsPacket(counts, replace, hasNetwork, netId, resultItem, resultCount);
    }

    /** 客户端处理：无网络则清空缓存；有网络则更新缓存并显示合成结果提示 */
    public static void handle(NetworkItemCountsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!ctx.get().getDirection().getReceptionSide().isClient()) return;
            if (!msg.hasNetwork) {
                NetworkItemCache.setAll(msg.counts, false, msg.netId);
                return;
            }
            NetworkItemCache.setAll(msg.counts, true, msg.netId);
            CraftToast.show(msg.resultItem, msg.resultCount);
        });
        ctx.get().setPacketHandled(true);
    }
}
