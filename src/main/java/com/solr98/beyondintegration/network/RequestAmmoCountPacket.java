package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 弹药数量查询请求包（C2S）。
 * <p>携带要查询的弹药 ID 列表（通常由客户端从**背包内的枪**解析去重得到），
 * 服务端只对这些 ID 做定向查询并回发 {@link AmmoCountResponsePacket}（partial）；
 * 列表为空表示全量查询（兼容旧行为）。只有定向查询才走 O(ID 数) 的精确键查询，
 * 不做全桶扫描。
 */
public class RequestAmmoCountPacket {

    /** 要查询的弹药 ID 列表（空 = 全量） */
    private final List<ResourceLocation> ammoIds;

    /** 空参构造：全量查询 */
    public RequestAmmoCountPacket() {
        this(List.of());
    }

    /** 定向构造：只查询给定弹药 ID（预取背包内枪械所需弹药） */
    public RequestAmmoCountPacket(List<ResourceLocation> ammoIds) {
        this.ammoIds = ammoIds == null ? List.of() : List.copyOf(ammoIds);
    }

    public static void encode(RequestAmmoCountPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ammoIds.size());
        for (ResourceLocation id : msg.ammoIds) {
            buf.writeResourceLocation(id);
        }
    }

    public static RequestAmmoCountPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        return new RequestAmmoCountPacket(ids);
    }

    /** 服务端执行：定向（或全量）统计主网络弹药计数与网络名，打包 AmmoCountResponsePacket 回发 */
    public static void handle(RequestAmmoCountPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary == null) return;

            boolean partial = !msg.ammoIds.isEmpty();
            Map<String, Integer> ammoMap = new LinkedHashMap<>();
            if (partial) {
                for (ResourceLocation id : msg.ammoIds) {
                    if (id == null) continue;
                    ammoMap.put(id.toString(),
                            TaczAmmoExtractor.countAmmoInNetworkByAmmoId(id, primary));
                }
            } else {
                ammoMap.putAll(TaczAmmoExtractor.countAllAmmoInNetwork(primary));
            }

            String netName = primary instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketHandler.sendToPlayer(player, new AmmoCountResponsePacket(primary.getId(), netName, ammoMap, partial));
        });
        ctx.get().setPacketHandled(true);
    }
}
