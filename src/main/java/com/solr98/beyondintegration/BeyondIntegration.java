package com.solr98.beyondintegration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.PlayerNetworkSyncHandler;
import com.solr98.beyondintegration.handler.VehicleInteractHandler;
import com.solr98.beyondintegration.handler.YwzjVehicleSyncHandler;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(BeyondIntegration.MODID)
public class BeyondIntegration {

    public static final String MODID = "beyond_integration";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BeyondIntegration(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Mod constructing");

        modEventBus.addListener(FMLCommonSetupEvent.class, this::commonSetup);

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommandConfig.SERVER_SPEC);
    }

    public void commonSetup(final FMLCommonSetupEvent event) {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.handler.ItemBlacklistHandler());
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler());
        LOGGER.info("Registered ItemBlacklistHandler");

        // VehicleInteractHandler uses reflection to detect SW/ywzj vehicles,
        // so it is safe to register regardless of which mods are loaded.
        NeoForge.EVENT_BUS.register(new VehicleInteractHandler());
        LOGGER.info("Registered VehicleInteractHandler");

        if (ModList.get().isLoaded("ywzj_vehicle")) {
            NeoForge.EVENT_BUS.register(new YwzjVehicleSyncHandler());
            LOGGER.info("Registered YwzjVehicleSyncHandler");
        }

        if (ModList.get().isLoaded("superbwarfare")) {
            NeoForge.EVENT_BUS.register(new PlayerNetworkSyncHandler());
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.handler.SuperbAmmoInsertHandler());
            LOGGER.info("Registered SW handlers");
        }

        if (ModList.get().isLoaded("tacz")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.handler.AmmoBoxExtractHandler());
            LOGGER.info("Registered TACZ handlers");
        }
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.solr98.beyondintegration.command.BDNetworkCommands.onRegisterCommands(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
