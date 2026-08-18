package com.solr98.beyondintegration.network;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * ITEM 型弹药"现查现用"请求：客户端按需查询某网络的指定物品弹药计数。
 * itemKeys 为 "ITEM:<注册名>" 格式。
 */
public class RequestItemAmmoPacket {

    /** 目标网络的网络 ID */
    private final int netId;
    /** 要查询的弹药物品键列表（"ITEM:<注册名>" 格式） */
    private final List<String> itemKeys;

    /** 构造请求：克隆传入键列表，防止外部修改 */
    public RequestItemAmmoPacket(int netId, List<String> itemKeys) {
        this.netId = netId;
        this.itemKeys = itemKeys != null ? new ArrayList<>(itemKeys) : new ArrayList<>();
    }

    /** 编码：写入网络 ID、键数量及每个键字符串 */
    public static void encode(RequestItemAmmoPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.itemKeys.size());
        for (String key : msg.itemKeys) {
            buf.writeUtf(key);
        }
    }

    /** 解码：按编码顺序读出网络 ID 与键列表 */
    public static RequestItemAmmoPacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        int size = buf.readVarInt();
        List<String> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            keys.add(buf.readUtf());
        }
        return new RequestItemAmmoPacket(netId, keys);
    }

    /** 服务端处理：从网络中逐键查询物品计数，非空则回发 ItemAmmoResponsePacket */
    public static void handle(RequestItemAmmoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.itemKeys.isEmpty()) return;

            DimensionsNet dimNet = DimensionsNet.getNetFromId(msg.netId);
            if (dimNet == null) return;

            Map<String, Long> counts = new HashMap<>();
            for (String key : msg.itemKeys) {
                String raw = key.startsWith("ITEM:") ? key.substring(5) : key;
                ResourceLocation loc = ResourceLocation.tryParse(raw);
                if (loc == null) continue;
                ItemStack ref = new ItemStack(ForgeRegistries.ITEMS.getValue(loc));
                if (ref.isEmpty()) continue;
                long count = dimNet.getUnifiedStorage().getStackByKey(new ItemStackKey(ref)).amount();
                counts.put(key, count);
            }

            if (!counts.isEmpty()) {
                PacketHandler.sendToPlayer(player, new ItemAmmoResponsePacket(msg.netId, counts));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
