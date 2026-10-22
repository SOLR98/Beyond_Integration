package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主网络同步包（S2C，会话级，非菜单绑定）。
 * <p>携带 {@code typeMask}（本范围可用类型，声明客户端能力）与：
 * <ul>
 *   <li>一批 {@code IStackKey} + 绝对数量（storage 键）；</li>
 *   <li>可选 {@code EXT_AMMO} 虚拟弹药映射、{@code EXT_FLAGS} 网络名 / 开关位。</li>
 * </ul>
 * {@code clear=true} 清空客户端镜像。客户端经 {@link PrimaryNetClientStorage#INSTANCE}
 * 供 JEI 等多种功能按主网络显示 / 取物与条件取数。
 */
public record PrimaryNetSyncPayload(boolean clear, boolean hasNetwork, int netId, int typeMask,
                                    List<IStackKey<?>> keys, List<Long> counts,
                                    Map<String, Long> extAmmo, String name, long flagsBits)
        implements CustomPacketPayload {

    public static final Type<PrimaryNetSyncPayload> TYPE =
            new Type<>(ResourceLocation.parse("beyond_integration:primary_net_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrimaryNetSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PrimaryNetSyncPayload decode(RegistryFriendlyByteBuf buf) {
            boolean clear = buf.readBoolean();
            boolean hasNetwork = buf.readBoolean();
            int netId = buf.readVarInt();
            int typeMask = buf.readVarInt();
            int n = buf.readVarInt();
            List<IStackKey<?>> keys = new ArrayList<>(n);
            List<Long> counts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                keys.add(IStackKey.STREAM_CODEC.decode(buf));
                counts.add(buf.readVarLong());
            }
            Map<String, Long> extAmmo = null;
            String name = null;
            long flagsBits = 0L;
            if ((typeMask & NetDataType.EXT_AMMO.bit()) != 0) {
                int size = buf.readVarInt();
                extAmmo = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    extAmmo.put(buf.readUtf(), buf.readVarLong());
                }
            }
            if ((typeMask & NetDataType.EXT_FLAGS.bit()) != 0) {
                name = buf.readUtf();
                flagsBits = buf.readVarLong();
            }
            return new PrimaryNetSyncPayload(clear, hasNetwork, netId, typeMask, keys, counts, extAmmo, name, flagsBits);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PrimaryNetSyncPayload payload) {
            buf.writeBoolean(payload.clear);
            buf.writeBoolean(payload.hasNetwork);
            buf.writeVarInt(payload.netId);
            buf.writeVarInt(payload.typeMask);
            buf.writeVarInt(payload.keys.size());
            for (int i = 0; i < payload.keys.size(); i++) {
                IStackKey.STREAM_CODEC.encode(buf, payload.keys.get(i));
                buf.writeVarLong(payload.counts.get(i));
            }
            if ((payload.typeMask & NetDataType.EXT_AMMO.bit()) != 0) {
                Map<String, Long> ammo = payload.extAmmo == null ? Map.of() : payload.extAmmo;
                buf.writeVarInt(ammo.size());
                for (Map.Entry<String, Long> e : ammo.entrySet()) {
                    buf.writeUtf(e.getKey());
                    buf.writeVarLong(e.getValue());
                }
            }
            if ((payload.typeMask & NetDataType.EXT_FLAGS.bit()) != 0) {
                buf.writeUtf(payload.name == null ? "" : payload.name);
                buf.writeVarLong(payload.flagsBits);
            }
        }
    };

    public static void handle(final PrimaryNetSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<IStackKey<?>, Long> map = new HashMap<>(payload.keys.size());
            int n = Math.min(payload.keys.size(), payload.counts.size());
            for (int i = 0; i < n; i++) {
                map.put(payload.keys.get(i), payload.counts.get(i));
            }
            PrimaryNetClientStorage.INSTANCE.apply(
                    payload.clear, payload.hasNetwork, payload.netId, payload.typeMask,
                    map, payload.extAmmo, payload.name, payload.flagsBits);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
