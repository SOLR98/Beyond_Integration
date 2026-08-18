package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * SW 固定项增量/全量推送包。
 * full=true 时 ammo 为全量、energy 为绝对值；否则 ammo 为增量、energy 为 delta。
 */
public class SuperbAmmoDeltaS2CPacket {

    /** 目标网络 ID */
    private final int netId;
    /** 是否属于载具侧数据 */
    private final boolean isVehicle;
    /** true=全量推送，false=增量推送 */
    private final boolean full;
    /** 弹药变化映射（全量或增量） */
    private final Map<String, Long> ammo;
    /** 能量值（全量时绝对值，增量时为 delta） */
    private final long energy;
    /** 网络自定义名称 */
    private final String netName;
    /** 附魔分离开关状态 */
    private final boolean enchantSeparation;

    /** 构造推送包，网络名称为空时归一为空串 */
    public SuperbAmmoDeltaS2CPacket(int netId, boolean isVehicle, boolean full,
                                    Map<String, Long> ammo, long energy, String netName,
                                    boolean enchantSeparation) {
        this.netId = netId;
        this.isVehicle = isVehicle;
        this.full = full;
        this.ammo = ammo;
        this.energy = energy;
        this.netName = netName != null ? netName : "";
        this.enchantSeparation = enchantSeparation;
    }

    /** 编码：按字段顺序写入网络 ID、标记、名称、能量、开关及弹药映射 */
    public static void encode(SuperbAmmoDeltaS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeBoolean(msg.isVehicle);
        buf.writeBoolean(msg.full);
        buf.writeUtf(msg.netName);
        buf.writeVarLong(msg.energy);
        buf.writeBoolean(msg.enchantSeparation);
        buf.writeVarInt(msg.ammo.size());
        for (var entry : msg.ammo.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarLong(entry.getValue());
        }
    }

    /** 解码：按编码顺序还原全部字段 */
    public static SuperbAmmoDeltaS2CPacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        boolean isVehicle = buf.readBoolean();
        boolean full = buf.readBoolean();
        String netName = buf.readUtf();
        long energy = buf.readVarLong();
        boolean enchantSep = buf.readBoolean();
        int size = buf.readVarInt();
        Map<String, Long> ammo = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            ammo.put(buf.readUtf(), buf.readVarLong());
        }
        return new SuperbAmmoDeltaS2CPacket(netId, isVehicle, full, ammo, energy, netName, enchantSep);
    }

    /** 客户端处理：将增量/全量数据应用到 SuperbAmmoCache 缓存 */
    public static void handle(SuperbAmmoDeltaS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.applyDelta(msg.netId, msg.isVehicle, msg.full, msg.ammo,
                        msg.energy, msg.netName, msg.enchantSeparation);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
