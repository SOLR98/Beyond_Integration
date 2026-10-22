package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig.PrimaryNetSyncScope;
import com.solr98.beyondintegration.client.SyncConfigClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2C：服务端配置整包下发（全部 {@code ServerConfig} 配置项 + 主网络同步总开关/范围）。
 * <p>服务端在玩家登录与配置重载时推送，作为客户端条件取数 / 类型位判断的权威依据。
 */
public record SyncConfigSyncPayload(boolean enabled, int scopeOrdinal, Map<String, String> allConfig)
        implements CustomPacketPayload {

    public static final Type<SyncConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.parse("beyond_integration:sync_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfigSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SyncConfigSyncPayload decode(RegistryFriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            int scope = buf.readVarInt();
            int n = buf.readVarInt();
            Map<String, String> all = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                all.put(buf.readUtf(256), buf.readUtf(32767));
            }
            return new SyncConfigSyncPayload(enabled, scope, all);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncConfigSyncPayload payload) {
            buf.writeBoolean(payload.enabled);
            buf.writeVarInt(payload.scopeOrdinal);
            Map<String, String> all = payload.allConfig == null ? Map.of() : payload.allConfig;
            buf.writeVarInt(all.size());
            for (Map.Entry<String, String> e : all.entrySet()) {
                buf.writeUtf(e.getKey(), 256);
                buf.writeUtf(e.getValue() == null ? "" : e.getValue(), 32767);
            }
        }
    };

    public static void handle(final SyncConfigSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            PrimaryNetSyncScope[] values = PrimaryNetSyncScope.values();
            int ord = payload.scopeOrdinal;
            PrimaryNetSyncScope scope = (ord >= 0 && ord < values.length) ? values[ord] : PrimaryNetSyncScope.ITEMS;
            SyncConfigClient.set(payload.enabled, scope, payload.allConfig);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
