package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/** 客户端缓存过期时的全量拉取请求（仅固定项；ITEM 弹药走 RequestItemAmmoPacket 现查） */
public record RequestSuperbAmmoStatusPacket() implements CustomPacketPayload {

    public static final Type<RequestSuperbAmmoStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_superb_ammo_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSuperbAmmoStatusPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestSuperbAmmoStatusPacket());

    /** 服务端处理：汇总主网络的弹药、能量、附魔分离开关与名称，回发 SuperbAmmoStatusResponsePacket */
    public static void handle(final RequestSuperbAmmoStatusPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;

            Map<String, Long> fullMap = com.solr98.beyondintegration.feature.ammo.sw.SwAmmoStorage.snapshot(net);

            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            boolean enchantSep = net instanceof EnchantSeparationAccessor ea
                    && ea.beyond$isEnchantSeparationEnabled();
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketHandler.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                    net.getId(), netName, energy, enchantSep, fullMap, null));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
