package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 主网络物品计数同步（S2C，会话级，非菜单绑定）。
 * <p>
 * 携带一批 {@code IStackKey}（物品+NBT，经 BD {@code IStackKey.STREAM_CODEC}）与其绝对数量；
 * {@code clear=true} 时清空客户端缓存。客户端写入 {@link PrimaryNetClientStorage}，供 JEI 在任意界面使用。
 */
public record PrimaryNetSyncPayload(boolean clear, boolean hasNetwork, int netId,
                                    List<IStackKey<?>> keys, List<Long> counts) implements CustomPacketPayload {

    public static final Type<PrimaryNetSyncPayload> TYPE =
            new Type<>(ResourceLocation.parse("beyond_integration:primary_net_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrimaryNetSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PrimaryNetSyncPayload decode(RegistryFriendlyByteBuf buf) {
            boolean clear = buf.readBoolean();
            boolean hasNetwork = buf.readBoolean();
            int netId = buf.readVarInt();
            int n = buf.readVarInt();
            List<IStackKey<?>> keys = new ArrayList<>(n);
            List<Long> counts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                keys.add(IStackKey.STREAM_CODEC.decode(buf));
                counts.add(buf.readVarLong());
            }
            return new PrimaryNetSyncPayload(clear, hasNetwork, netId, keys, counts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PrimaryNetSyncPayload payload) {
            buf.writeBoolean(payload.clear);
            buf.writeBoolean(payload.hasNetwork);
            buf.writeVarInt(payload.netId);
            buf.writeVarInt(payload.keys.size());
            for (int i = 0; i < payload.keys.size(); i++) {
                IStackKey.STREAM_CODEC.encode(buf, payload.keys.get(i));
                buf.writeVarLong(payload.counts.get(i));
            }
        }
    };

    public static void handle(final PrimaryNetSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> PrimaryNetClientStorage.apply(
                payload.clear, payload.hasNetwork, payload.netId, payload.keys, payload.counts));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
