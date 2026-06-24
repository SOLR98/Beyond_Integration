package com.solr98.beyondintegration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.blacklist.ItemBlacklistHandler;
import com.solr98.beyondintegration.feature.vehicle.VehicleInteractHandler;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.solr98.beyondintegration.integration.SuperbWarfareIntegration;
import com.solr98.beyondintegration.integration.TaczAddonIntegration;
import com.solr98.beyondintegration.integration.TaczIntegration;
import com.solr98.beyondintegration.integration.YwzjIntegration;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(BeyondIntegration.MODID)
public class BeyondIntegration {

    public static final String MODID = "beyond_integration";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BeyondIntegration(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(FMLCommonSetupEvent.class, this::commonSetup);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
        modContainer.registerConfig(ModConfig.Type.COMMON, CommandConfig.SERVER_SPEC);
    }

    public void commonSetup(final FMLCommonSetupEvent event) {
        registerCoreHandlers();
        registerIntegrations();
    }

    private void registerCoreHandlers() {
        UnifiedStorageBeforeInsertHandler.addHandler(new ItemBlacklistHandler());
        UnifiedStorageBeforeInsertHandler.addHandler(new EnchantmentBookSeparatorHandler());
        NeoForge.EVENT_BUS.register(new VehicleInteractHandler());
        LOGGER.info("[BeyondIntegration] Core handlers registered");
    }

    private void registerIntegrations() {
        SuperbWarfareIntegration.registerIfPresent();
        TaczIntegration.registerIfPresent();
        TaczAddonIntegration.registerIfPresent();
        YwzjIntegration.registerIfPresent();
        LOGGER.info("[BeyondIntegration] All integrations registered");
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.solr98.beyondintegration.command.BDNetworkCommands.onRegisterCommands(event);
    }
}
