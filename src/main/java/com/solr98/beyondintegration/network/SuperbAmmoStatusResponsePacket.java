package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * SW 固定项全量快照包（玩家侧/载具侧通用）。
 * ammoList：载具可用弹药列表（"ITEM:<注册名>" 或弹药 serializationName），玩家侧为 null。
 */
public class SuperbAmmoStatusResponsePacket {

    /** 网络 ID */
    private final int netId;
    /** 是否载具数据（由 ammoList 是否为空推断） */
    private final boolean isVehicle;
    /** 网络自定义名称 */
    private final String name;
    /** 网络能量值 */
    private final long energy;
    /** 附魔分离开关状态 */
    private final boolean enchantSeparation;
    /** 弹药计数映射（全量快照） */
    private final Map<String, Long> ammo;
    /** 载具可用弹药列表（"ITEM:<注册名>" 或 serializationName），玩家侧为 null */
    private final List<String> ammoList;

    /** 构造快照包：空值安全，null 名/空映射统一归一 */
    public SuperbAmmoStatusResponsePacket(int netId, String name, long energy, boolean enchantSeparation,
                                          Map<String, Long> ammo, List<String> ammoList) {
        this.netId = netId;
        this.isVehicle = ammoList != null;
        this.name = name != null ? name : "";
        this.energy = energy;
        this.enchantSeparation = enchantSeparation;
        this.ammo = ammo != null ? new HashMap<>(ammo) : new HashMap<>();
        this.ammoList = ammoList != null ? new ArrayList<>(ammoList) : null;
    }

    /** 服务端便捷构造：从网络对象读取 ID、附魔分离开关并生成玩家侧快照 */
    public static SuperbAmmoStatusResponsePacket fromNet(DimensionsNet net, Map<String, Long> ammo,
                                                         long energy, String name) {
        boolean enchantSep = net instanceof EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled();
        return new SuperbAmmoStatusResponsePacket(net.getId(), name, energy, enchantSep, ammo, null);
    }

    /** 编码：写入基础字段、弹药映射，载具侧额外写入弹药列表 */
    public static void encode(SuperbAmmoStatusResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeBoolean(msg.isVehicle);
        buf.writeUtf(msg.name);
        buf.writeVarLong(msg.energy);
        buf.writeBoolean(msg.enchantSeparation);
        buf.writeVarInt(msg.ammo.size());
        for (var entry : msg.ammo.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarLong(entry.getValue());
        }
        if (msg.isVehicle) {
            buf.writeVarInt(msg.ammoList.size());
            for (String key : msg.ammoList) {
                buf.writeUtf(key);
            }
        }
    }

    /** 解码：按编码顺序还原，载具侧读回弹药列表 */
    public static SuperbAmmoStatusResponsePacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        boolean isVehicle = buf.readBoolean();
        String name = buf.readUtf();
        long energy = buf.readVarLong();
        boolean enchantSep = buf.readBoolean();
        int size = buf.readVarInt();
        Map<String, Long> ammo = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ammo.put(buf.readUtf(), buf.readVarLong());
        }
        List<String> ammoList = null;
        if (isVehicle) {
            int listSize = buf.readVarInt();
            ammoList = new ArrayList<>(listSize);
            for (int i = 0; i < listSize; i++) {
                ammoList.add(buf.readUtf());
            }
        }
        return new SuperbAmmoStatusResponsePacket(netId, name, energy, enchantSep, ammo, ammoList);
    }

    /** 客户端处理：用快照整体刷新 SuperbAmmoCache 缓存 */
    public static void handle(SuperbAmmoStatusResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.update(msg.netId, msg.isVehicle, msg.name, msg.ammo,
                        msg.energy, msg.enchantSeparation, msg.ammoList);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
