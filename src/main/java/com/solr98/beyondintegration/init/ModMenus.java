package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.crafting.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, BeyondIntegration.MODID);
    public static final RegistryObject<MenuType<DimensionsStorageMenu>> STORAGE = MENU_TYPES.register("storage",()->IForgeMenuType.create(DimensionsStorageMenu::new));
    public static final RegistryObject<MenuType<DimensionsAnvilMenu>> ANVIL = MENU_TYPES.register("anvil",()->IForgeMenuType.create(DimensionsAnvilMenu::new));
    public static final RegistryObject<MenuType<DimensionsCutMenu>> CUT = MENU_TYPES.register("cut",()->IForgeMenuType.create(DimensionsCutMenu::new));
    public static final RegistryObject<MenuType<DimensionsGrindMenu>> GRIND = MENU_TYPES.register("grind",()->IForgeMenuType.create(DimensionsGrindMenu::new));
    public static final RegistryObject<MenuType<DimensionsSmithMenu>> SMITH = MENU_TYPES.register("smith",()->IForgeMenuType.create(DimensionsSmithMenu::new));
    public static final RegistryObject<MenuType<DimensionsCraftMenu>> CRAFT = MENU_TYPES.register("craft",()->IForgeMenuType.create(DimensionsCraftMenu::new));
    public static void register(IEventBus b){MENU_TYPES.register(b);}
}
