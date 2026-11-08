package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope;
import com.solr98.beyondintegration.client.SyncConfigClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C：服务端配置整包下发（全部 {@code ServerConfig} 配置项 + 主网络同步总开关/范围）。
 * <p>服务端在玩家登录与配置重载时推送，作为客户端条件取数 / 类型位判断的权威依据。
 */
public final class SyncConfigSyncPacket {

    private final boolean enabled;
    private final int scopeOrdinal;
    private final Map<String, String> allConfig;

    public SyncConfigSyncPacket(boolean enabled, int scopeOrdinal, Map<String, String> allConfig) {
        this.enabled = enabled;
        this.scopeOrdinal = scopeOrdinal;
        this.allConfig = allConfig == null ? Map.of() : Map.copyOf(allConfig);
    }

    public static void encode(SyncConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.scopeOrdinal);
        buf.writeVarInt(msg.allConfig.size());
        for (Map.Entry<String, String> e : msg.allConfig.entrySet()) {
            buf.writeUtf(e.getKey(), 256);
            buf.writeUtf(e.getValue() == null ? "" : e.getValue(), 32767);
        }
    }

    public static SyncConfigSyncPacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int scope = buf.readVarInt();
        int n = buf.readVarInt();
        Map<String, String> all = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            all.put(buf.readUtf(256), buf.readUtf(32767));
        }
        return new SyncConfigSyncPacket(enabled, scope, all);
    }

    public static void handle(SyncConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            PrimaryNetSyncScope[] values = PrimaryNetSyncScope.values();
            int ord = msg.scopeOrdinal;
            PrimaryNetSyncScope scope = (ord >= 0 && ord < values.length) ? values[ord] : PrimaryNetSyncScope.ITEMS;
            SyncConfigClient.set(msg.enabled, scope, msg.allConfig);
        }));
        ctx.get().setPacketHandled(true);
    }
}
