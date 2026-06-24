package com.solr98.beyondintegration.client;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.config.ModConfigScreen;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        var mc = net.neoforged.fml.ModList.get().getModContainerById(BeyondIntegration.MODID);
        mc.ifPresent(c -> {
            if (ModList.get().isLoaded("cloth_config")) {
                c.registerExtensionPoint(IConfigScreenFactory.class,
                        (screen, parent) -> ModConfigScreen.createScreen(parent));
            }
        });
        NeoForge.EVENT_BUS.register(new ItemTooltipHandler());
        if (ModList.get().isLoaded("tacz")) {
            TaczClientRegistrar.register();
        }
    }
}
