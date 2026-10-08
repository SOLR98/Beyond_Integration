package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 主网络物品计数同步包（S2C，会话级，非菜单绑定）。
 * <p>
 * 携带一批 {@code IStackKey}（物品+NBT，经 BD 通用序列化）与其绝对数量；{@code clear=true} 时清空客户端缓存。
 * 客户端写入 {@link PrimaryNetClientStorage}，供 JEI 在任意界面按主网络显示/取物。
 */
public class PrimaryNetSyncPacket {

    private final boolean clear;
    private final boolean hasNetwork;
    private final int netId;
    private final List<IStackKey<?>> keys;
    private final List<Long> counts;

    public PrimaryNetSyncPacket(boolean clear, boolean hasNetwork, int netId,
                                List<IStackKey<?>> keys, List<Long> counts) {
        this.clear = clear;
        this.hasNetwork = hasNetwork;
        this.netId = netId;
        this.keys = keys;
        this.counts = counts;
    }

    public static void encode(PrimaryNetSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.clear);
        buf.writeBoolean(msg.hasNetwork);
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.keys.size());
        for (int i = 0; i < msg.keys.size(); i++) {
            IStackKey.serializeCommon(buf, msg.keys.get(i));
            buf.writeVarLong(msg.counts.get(i));
        }
    }

    public static PrimaryNetSyncPacket decode(FriendlyByteBuf buf) {
        boolean clear = buf.readBoolean();
        boolean hasNetwork = buf.readBoolean();
        int netId = buf.readVarInt();
        int n = buf.readVarInt();
        List<IStackKey<?>> keys = new ArrayList<>(n);
        List<Long> counts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keys.add(IStackKey.deserializeCommon(buf));
            counts.add(buf.readVarLong());
        }
        return new PrimaryNetSyncPacket(clear, hasNetwork, netId, keys, counts);
    }

    public static void handle(PrimaryNetSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> PrimaryNetClientStorage.apply(
                        msg.clear, msg.hasNetwork, msg.netId, msg.keys, msg.counts)));
        ctx.get().setPacketHandled(true);
    }
}
