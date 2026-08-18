package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 附魔分离开关同步包（S2C）：服务端将当前网络的附魔分离启用状态同步给客户端，供 UI 显示使用 */
public class EnchantSeparationSyncPacket {

    /** 附魔分离是否启用 */
    private final boolean enabled;

    public EnchantSeparationSyncPacket(boolean enabled) {
        this.enabled = enabled;
    }

    /** 写入启用标志到缓冲区 */
    public static void encode(EnchantSeparationSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
    }

    /** 从缓冲区读取启用标志并还原数据包 */
    public static EnchantSeparationSyncPacket decode(FriendlyByteBuf buf) {
        return new EnchantSeparationSyncPacket(buf.readBoolean());
    }

    /** 客户端收到后更新 SuperbAmmoCache 中的附魔分离状态 */
    public static void handle(EnchantSeparationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                SuperbAmmoCache.setEnchantSeparation(msg.enabled);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
