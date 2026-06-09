package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.config.ModConfigScreen;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        var mc = net.neoforged.fml.ModList.get().getModContainerById(BeyondIntegration.MODID);
        mc.ifPresent(c -> {
            ModContainer container = c;
            if (ModList.get().isLoaded("cloth_config")) {
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (screen, parent) -> ModConfigScreen.createScreen(parent));
            }
        });
        NeoForge.EVENT_BUS.register(new ItemTooltipHandler());
        if (ModList.get().isLoaded("tacz")) {
            try {
                Class.forName("com.solr98.beyondintegration.client.TaczClientRegistrar")
                        .getMethod("register")
                        .invoke(null);
            } catch (Exception ignored) {}
        }
    }
}
