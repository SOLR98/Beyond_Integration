package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.config.ModConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

public class ClientRegistrar {

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> {
                            if (ModList.get().isLoaded("cloth_config")) {
                                try {
                                    return ModConfigScreen.createScreen(parent);
                                } catch (NoClassDefFoundError ignored) {}
                            }
                            return parent;
                        }));
    }
}
