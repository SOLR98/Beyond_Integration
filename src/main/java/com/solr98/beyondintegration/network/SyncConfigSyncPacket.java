package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope;
import com.solr98.beyondintegration.client.SyncConfigClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C：主网络同步运行时配置推送（总开关 + 范围）。
 * <p>服务端在玩家登录与配置重载时推送，作为客户端条件取数 / 类型位判断的权威依据。
 */
public final class SyncConfigSyncPacket {

    private final boolean enabled;
    private final int scopeOrdinal;

    public SyncConfigSyncPacket(boolean enabled, int scopeOrdinal) {
        this.enabled = enabled;
        this.scopeOrdinal = scopeOrdinal;
    }

    public static void encode(SyncConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.scopeOrdinal);
    }

    public static SyncConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new SyncConfigSyncPacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(SyncConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            PrimaryNetSyncScope[] values = PrimaryNetSyncScope.values();
            int ord = msg.scopeOrdinal;
            PrimaryNetSyncScope scope = (ord >= 0 && ord < values.length) ? values[ord] : PrimaryNetSyncScope.ITEMS;
            SyncConfigClient.set(msg.enabled, scope);
        }));
        ctx.get().setPacketHandled(true);
    }
}
