package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = BeyondIntegration.MODID)
public class PacketHandler {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playBidirectional(SuperbAmmoStatusResponsePacket.TYPE, SuperbAmmoStatusResponsePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SuperbAmmoStatusResponsePacket::handle, SuperbAmmoStatusResponsePacket::handle));
        registrar.playToServer(RequestSuperbAmmoStatusPacket.TYPE, RequestSuperbAmmoStatusPacket.STREAM_CODEC,
                RequestSuperbAmmoStatusPacket::handle);
        registrar.playToServer(RequestSuperbAmmoExtractPacket.TYPE, RequestSuperbAmmoExtractPacket.STREAM_CODEC,
                RequestSuperbAmmoExtractPacket::handle);
        registrar.playToServer(ToggleEnchantSeparationPacket.TYPE, ToggleEnchantSeparationPacket.STREAM_CODEC,
                ToggleEnchantSeparationPacket::handle);
        registrar.playToServer(RequestAmmoCountPacket.TYPE, RequestAmmoCountPacket.STREAM_CODEC,
                RequestAmmoCountPacket::handle);
        registrar.playToClient(AmmoCountResponsePacket.TYPE, AmmoCountResponsePacket.STREAM_CODEC,
                AmmoCountResponsePacket::handle);
        registrar.playToServer(RequestNetworkItemsPacket.TYPE, RequestNetworkItemsPacket.STREAM_CODEC,
                RequestNetworkItemsPacket::handle);
        registrar.playToClient(NetworkItemCountsPacket.TYPE, NetworkItemCountsPacket.STREAM_CODEC,
                NetworkItemCountsPacket::handle);
        registrar.playToServer(TaczCraftPacket.TYPE, TaczCraftPacket.STREAM_CODEC,
                TaczCraftPacket::handle);
        registrar.playToClient(YwzjVehicleDataResponsePacket.TYPE, YwzjVehicleDataResponsePacket.STREAM_CODEC,
                YwzjVehicleDataResponsePacket::handle);
        registrar.playToServer(ProtectItemPacket.TYPE, ProtectItemPacket.STREAM_CODEC,
                ProtectItemPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
