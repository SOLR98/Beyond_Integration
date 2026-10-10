package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 网络药水护符状态同步包（S2C）：同步"生效目标 + 经验修补是否已献祭解锁"，供 UI 显示使用 */
public class PotionCharmSyncPacket {

    /** 生效目标（PotionCharmMode 序号） */
    private final int mode;
    /** 经验修补是否已献祭解锁 */
    private final boolean mendingUnlocked;

    public PotionCharmSyncPacket(int mode, boolean mendingUnlocked) {
        this.mode = mode;
        this.mendingUnlocked = mendingUnlocked;
    }

    public static void encode(PotionCharmSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
        buf.writeBoolean(msg.mendingUnlocked);
    }

    public static PotionCharmSyncPacket decode(FriendlyByteBuf buf) {
        return new PotionCharmSyncPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PotionCharmSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.setPotionCharmState(msg.mode, msg.mendingUnlocked);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
