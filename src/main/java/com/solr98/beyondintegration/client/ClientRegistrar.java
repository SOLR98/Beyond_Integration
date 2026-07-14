package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.config.ModConfigScreen;
import com.solr98.beyondintegration.client.gui.*;
import com.solr98.beyondintegration.feature.crafting.*;
import com.solr98.beyondintegration.init.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ClientRegistrar {
    @OnlyIn(Dist.CLIENT) @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,()->new ConfigScreenHandler.ConfigScreenFactory((mc,parent)->{if(ModList.get().isLoaded("cloth_config")){try{return ModConfigScreen.createScreen(parent);}catch(NoClassDefFoundError e){}}return parent;}));
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e)->{e.enqueueWork(()->{
            MenuScreens.<DimensionsStorageMenu,DimensionsStorageGUI<DimensionsStorageMenu>>register(ModMenus.STORAGE.get(),(a,b,c)->new DimensionsStorageGUI<>(a,b,c));
            MenuScreens.register(ModMenus.ANVIL.get(),DimensionsAnvilGUI::new);
            MenuScreens.register(ModMenus.CUT.get(),DimensionsCutGUI::new);
            MenuScreens.register(ModMenus.GRIND.get(),DimensionsGrindGUI::new);
            MenuScreens.register(ModMenus.SMITH.get(),DimensionsSmithGUI::new);
            MenuScreens.register(ModMenus.CRAFT.get(),DimensionsCraftGUI::new);
        });});
        if(ModList.get().isLoaded("tacz")){
            MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent ev)->{
                if(ev.getLevel().isClientSide() && ev.getEntity()==net.minecraft.client.Minecraft.getInstance().player){
                    TaczAmmoCache.requestQuick(null);
                }
            });
        }
    }
}
