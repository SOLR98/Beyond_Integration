package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
public class PrimaryNetSyncPacket {

    private final boolean clear;
    private final boolean hasNetwork;
    private final int netId;
    private final int typeMask;
    private final List<IStackKey<?>> keys;
    private final List<Long> counts;
    private final Map<String, Long> extAmmo;
    private final String name;
    private final long flagsBits;

    public PrimaryNetSyncPacket(boolean clear, boolean hasNetwork, int netId,
                                List<IStackKey<?>> keys, List<Long> counts) {
        this(clear, hasNetwork, netId, 0, keys, counts, null, null, 0L);
    }

    public PrimaryNetSyncPacket(boolean clear, boolean hasNetwork, int netId, int typeMask,
                                List<IStackKey<?>> keys, List<Long> counts) {
        this(clear, hasNetwork, netId, typeMask, keys, counts, null, null, 0L);
    }

    public PrimaryNetSyncPacket(boolean clear, boolean hasNetwork, int netId, int typeMask,
                                List<IStackKey<?>> keys, List<Long> counts,
                                Map<String, Long> extAmmo, String name, long flagsBits) {
        this.clear = clear;
        this.hasNetwork = hasNetwork;
        this.netId = netId;
        this.typeMask = typeMask;
        this.keys = keys;
        this.counts = counts;
        this.extAmmo = extAmmo;
        this.name = name;
        this.flagsBits = flagsBits;
    }

    public static void encode(PrimaryNetSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.clear);
        buf.writeBoolean(msg.hasNetwork);
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.typeMask);
        buf.writeVarInt(msg.keys.size());
        for (int i = 0; i < msg.keys.size(); i++) {
            IStackKey.serializeCommon(buf, msg.keys.get(i));
            buf.writeVarLong(msg.counts.get(i));
        }
        if ((msg.typeMask & NetDataType.EXT_AMMO.bit()) != 0) {
            Map<String, Long> ammo = msg.extAmmo == null ? Map.of() : msg.extAmmo;
            buf.writeVarInt(ammo.size());
            for (Map.Entry<String, Long> e : ammo.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeVarLong(e.getValue());
            }
        }
        if ((msg.typeMask & NetDataType.EXT_FLAGS.bit()) != 0) {
            buf.writeUtf(msg.name == null ? "" : msg.name);
            buf.writeVarLong(msg.flagsBits);
        }
    }

    public static PrimaryNetSyncPacket decode(FriendlyByteBuf buf) {
        boolean clear = buf.readBoolean();
        boolean hasNetwork = buf.readBoolean();
        int netId = buf.readVarInt();
        int typeMask = buf.readVarInt();
        int n = buf.readVarInt();
        List<IStackKey<?>> keys = new ArrayList<>(n);
        List<Long> counts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keys.add(IStackKey.deserializeCommon(buf));
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
        return new PrimaryNetSyncPacket(clear, hasNetwork, netId, typeMask, keys, counts, extAmmo, name, flagsBits);
    }

    public static void handle(PrimaryNetSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Map<IStackKey<?>, Long> map = new HashMap<>(msg.keys.size());
            int n = Math.min(msg.keys.size(), msg.counts.size());
            for (int i = 0; i < n; i++) {
                map.put(msg.keys.get(i), msg.counts.get(i));
            }
            PrimaryNetClientStorage.INSTANCE.apply(
                    msg.clear, msg.hasNetwork, msg.netId, msg.typeMask, map, msg.extAmmo, msg.name, msg.flagsBits);
        }));
        ctx.get().setPacketHandled(true);
    }
}
