package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.ICleanableWorkstation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// 清空工作站材料：toStorage=true→网络优先，false→背包优先
/** 清空工作站材料包（C2S）：客户端请求将工作台内的材料移出，由服务端按 toStorage 决定优先送回网络还是背包 */
public class CleanWorkstationPacket {

    /** true→材料优先移入网络存储，false→优先移入背包 */
    private final boolean toStorage;

    public CleanWorkstationPacket(boolean toStorage) {
        this.toStorage = toStorage;
    }

    /** 写入 toStorage 标志到缓冲区 */
    public static void encode(CleanWorkstationPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.toStorage);
    }

    /** 从缓冲区读取 toStorage 标志并还原数据包 */
    public static CleanWorkstationPacket decode(FriendlyByteBuf buf) {
        return new CleanWorkstationPacket(buf.readBoolean());
    }

    /** 服务端执行：若玩家当前打开的是可清理的工作站菜单，则清空其材料槽 */
    public static void handle(CleanWorkstationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof ICleanableWorkstation ws)
                ws.cleanSlots(msg.toStorage);
        });
        ctx.get().setPacketHandled(true);
    }
}
