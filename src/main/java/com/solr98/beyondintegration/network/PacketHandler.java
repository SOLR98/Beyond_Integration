package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static int id = 0;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BeyondIntegration.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        INSTANCE.registerMessage(id++, RecipeFillPacket.class,
                RecipeFillPacket::encode,
                RecipeFillPacket::decode,
                RecipeFillPacket::handle);

        INSTANCE.registerMessage(id++, ProtectItemPacket.class,
                ProtectItemPacket::encode,
                ProtectItemPacket::decode,
                ProtectItemPacket::handle);

        INSTANCE.registerMessage(id++, NetworkItemCountsPacket.class,
                NetworkItemCountsPacket::encode,
                NetworkItemCountsPacket::decode,
                NetworkItemCountsPacket::handle);

        INSTANCE.registerMessage(id++, SetAnvilNamePacket.class,
                SetAnvilNamePacket::encode,
                SetAnvilNamePacket::decode,
                SetAnvilNamePacket::handle);

        INSTANCE.registerMessage(id++, OpenStorageMenuPacket.class,
                OpenStorageMenuPacket::encode,
                OpenStorageMenuPacket::decode,
                OpenStorageMenuPacket::handle);

        INSTANCE.registerMessage(id++, ToggleEnchantSeparationPacket.class,
                ToggleEnchantSeparationPacket::encode,
                ToggleEnchantSeparationPacket::decode,
                ToggleEnchantSeparationPacket::handle);

        INSTANCE.registerMessage(id++, EnchantSeparationSyncPacket.class,
                EnchantSeparationSyncPacket::encode,
                EnchantSeparationSyncPacket::decode,
                EnchantSeparationSyncPacket::handle);

        INSTANCE.registerMessage(id++, RequestEnchantSeparationPacket.class,
                RequestEnchantSeparationPacket::encode,
                RequestEnchantSeparationPacket::decode,
                RequestEnchantSeparationPacket::handle);

        if (ModList.get().isLoaded("superbwarfare")) {
            INSTANCE.registerMessage(id++, SuperbAmmoStatusResponsePacket.class,
                    SuperbAmmoStatusResponsePacket::encode,
                    SuperbAmmoStatusResponsePacket::decode,
                    SuperbAmmoStatusResponsePacket::handle);
            INSTANCE.registerMessage(id++, RequestSuperbAmmoStatusPacket.class,
                    RequestSuperbAmmoStatusPacket::encode,
                    RequestSuperbAmmoStatusPacket::decode,
                    RequestSuperbAmmoStatusPacket::handle);
            INSTANCE.registerMessage(id++, RequestSuperbAmmoExtractPacket.class,
                    RequestSuperbAmmoExtractPacket::encode,
                    RequestSuperbAmmoExtractPacket::decode,
                    RequestSuperbAmmoExtractPacket::handle);
        }

        if (ModList.get().isLoaded("tacz")) {
            INSTANCE.registerMessage(id++, RequestNetworkItemsPacket.class,
                    RequestNetworkItemsPacket::encode,
                    RequestNetworkItemsPacket::decode,
                    RequestNetworkItemsPacket::handle);
            INSTANCE.registerMessage(id++, TaczCraftPacket.class,
                    TaczCraftPacket::encode,
                    TaczCraftPacket::decode,
                    TaczCraftPacket::handle);
            INSTANCE.registerMessage(id++, RequestAmmoCountPacket.class,
                    RequestAmmoCountPacket::encode,
                    RequestAmmoCountPacket::decode,
                    RequestAmmoCountPacket::handle);
            INSTANCE.registerMessage(id++, AmmoCountResponsePacket.class,
                    AmmoCountResponsePacket::encode,
                    AmmoCountResponsePacket::decode,
                    AmmoCountResponsePacket::handle);
            INSTANCE.registerMessage(id++, TaczAmmoPushS2CPacket.class,
                    TaczAmmoPushS2CPacket::encode,
                    TaczAmmoPushS2CPacket::decode,
                    TaczAmmoPushS2CPacket::handle);
        }
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
