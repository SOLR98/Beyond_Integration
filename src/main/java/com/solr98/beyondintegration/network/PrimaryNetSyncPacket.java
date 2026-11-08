package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
 * {@code clear=true} 清空客户端镜像。客户端经 {@code PrimaryNetClientStorage.INSTANCE}
 * 供 JEI 等多种功能按主网络显示 / 取物与条件取数。
 */
public record PrimaryNetSyncPacket(boolean clear, boolean hasNetwork, int netId, int typeMask,
                                   List<IStackKey<?>> keys, List<Long> counts,
                                   Map<String, Long> extAmmo, String name, long flagsBits)
        implements CustomPacketPayload {

    public static final Type<PrimaryNetSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "primary_net_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrimaryNetSyncPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PrimaryNetSyncPacket decode(RegistryFriendlyByteBuf buf) {
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
                    return new PrimaryNetSyncPacket(clear, hasNetwork, netId, typeMask, keys, counts,
                            extAmmo, name, flagsBits);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PrimaryNetSyncPacket msg) {
                    buf.writeBoolean(msg.clear());
                    buf.writeBoolean(msg.hasNetwork());
                    buf.writeVarInt(msg.netId());
                    buf.writeVarInt(msg.typeMask());
                    buf.writeVarInt(msg.keys().size());
                    for (int i = 0; i < msg.keys().size(); i++) {
                        IStackKey.STREAM_CODEC.encode(buf, msg.keys().get(i));
                        buf.writeVarLong(msg.counts().get(i));
                    }
                    if ((msg.typeMask() & NetDataType.EXT_AMMO.bit()) != 0) {
                        Map<String, Long> ammo = msg.extAmmo() == null ? Map.of() : msg.extAmmo();
                        buf.writeVarInt(ammo.size());
                        for (Map.Entry<String, Long> e : ammo.entrySet()) {
                            buf.writeUtf(e.getKey());
                            buf.writeVarLong(e.getValue());
                        }
                    }
                    if ((msg.typeMask() & NetDataType.EXT_FLAGS.bit()) != 0) {
                        buf.writeUtf(msg.name() == null ? "" : msg.name());
                        buf.writeVarLong(msg.flagsBits());
                    }
                }
            };

    /** 简化构造：无扩展字段。 */
    public static PrimaryNetSyncPacket simple(boolean clear, boolean hasNetwork, int netId,
                                              List<IStackKey<?>> keys, List<Long> counts) {
        return new PrimaryNetSyncPacket(clear, hasNetwork, netId, 0, keys, counts, null, null, 0L);
    }

    public static void handle(final PrimaryNetSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) return;
            Map<IStackKey<?>, Long> map = new HashMap<>(packet.keys().size());
            int n = Math.min(packet.keys().size(), packet.counts().size());
            for (int i = 0; i < n; i++) {
                map.put(packet.keys().get(i), packet.counts().get(i));
            }
            PrimaryNetClientStorage.INSTANCE.apply(
                    packet.clear(), packet.hasNetwork(), packet.netId(), packet.typeMask(), map,
                    packet.extAmmo(), packet.name(), packet.flagsBits());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
