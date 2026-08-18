package com.solr98.beyondintegration.network;

import com.atsuishio.superbwarfare.data.gun.Ammo;
// DELETED: import com.solr98.beyondintegration.handler.BDNetworkHelper;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * C2S：客户端请求从"主网络"提取指定数量的 SW 固定弹药到玩家背包，
 * 服务端扣除库存、发放物品并回发最新弹药状态快照。
 */
public class RequestSuperbAmmoExtractPacket {

    /** 弹药类型键（serializationName），"__infinite__" 表示无限弹药 */
    private final String ammoType;
    /** 请求提取的数量 */
    private final long count;

    /** 构造提取请求 */
    public RequestSuperbAmmoExtractPacket(String ammoType, long count) {
        this.ammoType = ammoType;
        this.count = count;
    }

    /** 编码：写入弹药类型与数量 */
    public static void encode(RequestSuperbAmmoExtractPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.ammoType);
        buf.writeVarLong(msg.count);
    }

    /** 解码：读出弹药类型与数量 */
    public static RequestSuperbAmmoExtractPacket decode(FriendlyByteBuf buf) {
        return new RequestSuperbAmmoExtractPacket(buf.readUtf(), buf.readVarLong());
    }

    /** 服务端处理：校验并扣除弹药（无限弹药不扣），发放给玩家背包，回发状态快照 */
    public static void handle(RequestSuperbAmmoExtractPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.count <= 0) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

            Map<String, Long> ammoMap = acc.getSuperbAmmo();

            if ("__infinite__".equals(msg.ammoType)) return;

            // 先找弹药定义，找不到直接返回，避免先扣后找造成物品丢失
            Ammo ammo = findAmmoBySerialization(msg.ammoType);
            if (ammo == null) return;

            long infinite = ammoMap.getOrDefault("__infinite__", 0L);
            long current = ammoMap.getOrDefault(msg.ammoType, 0L);
            if (current <= 0 && infinite <= 0) return;

            long toExtract;
            if (infinite > 0) {
                toExtract = msg.count;
            } else {
                toExtract = Math.min(current, msg.count);
                long remaining = current - toExtract;
                if (remaining <= 0) {
                    ammoMap.remove(msg.ammoType);
                } else {
                    ammoMap.put(msg.ammoType, remaining);
                }
            }
            net.setDirty();

            {
                long giveCount = toExtract;
                while (giveCount > 0) {
                    int stackSize = (int) Math.min(giveCount, ammo.getItem().getMaxStackSize());
                    ItemStack ammoStack = new ItemStack(ammo.getItem(), stackSize);
                    if (!player.getInventory().add(ammoStack)) {
                        player.drop(ammoStack, false);
                    }
                    giveCount -= stackSize;
                }
            }

            Map<String, Long> fullMap = new HashMap<>(ammoMap);
            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketHandler.sendToPlayer(player, SuperbAmmoStatusResponsePacket.fromNet(
                    net, fullMap, energy, netName));
        });
        ctx.get().setPacketHandled(true);
    }

    /** 按 serializationName 查找对应的 Ammo 枚举，找不到返回 null */
    private static Ammo findAmmoBySerialization(String name) {
        for (Ammo ammo : Ammo.values()) {
            if (ammo.serializationName.equals(name)) return ammo;
        }
        return null;
    }
}
