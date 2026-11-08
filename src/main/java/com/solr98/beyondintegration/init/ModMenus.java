package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.common.menu.NetPathwayFilterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的菜单类型注册。
 * <p>当前仅有「维度网络通道过滤」一个菜单（BD 0.8.3 已自带其余机器界面，BI 不再自建）。
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BeyondIntegration.MODID);

    /** 维度网络通道（net_pathway）过滤界面。 */
    public static final RegistryObject<MenuType<NetPathwayFilterMenu>> NET_PATHWAY_FILTER =
            MENUS.register("net_pathway_filter",
                    () -> IForgeMenuType.create(NetPathwayFilterMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
