package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：客户端请求切换「主网络」的自动充电开关，
 * 服务端校验玩家为网络的经理/所有者后取反开关，并回发同步包。
 */
public record ToggleEnergyChargePacket() implements CustomPacketPayload {

    public static final Type<ToggleEnergyChargePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "toggle_energy_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleEnergyChargePacket> STREAM_CODEC =
            StreamCodec.unit(new ToggleEnergyChargePacket());

    public static void handle(final ToggleEnergyChargePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            if (!net.isManager(player) && !net.isOwner(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.beyond_integration.cannot_toggle_energy_charge"));
                return;
            }
            if (net instanceof EnergyChargeAccessor acc) {
                acc.beyond$setEnergyChargeEnabled(!acc.beyond$isEnergyChargeEnabled());
                net.setDirty();
                // 回发同步包：客户端独立状态（EnergyChargeState）据此回显，不依赖 SW 快照
                PacketHandler.sendToPlayer(player,
                        new EnergyChargeSyncPacket(net.getId(), acc.beyond$isEnergyChargeEnabled()));
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
