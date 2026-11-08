package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.hud.NetManaHudOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 客户端初始化入口（MOD 事件总线，仅客户端加载）。
 * <p>注册本模组自绘 HUD（复制配置提示 / 主网络同步状态 / 网络魔力）、HUD 位置编辑器快捷键，
 * 以及客户端 tick 与登出清理。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /** 客户端启动回调：注册 HUD 位置编辑器快捷键、Cloth Config 配置界面与客户端 tick / 登出清理。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
                if (e.phase == TickEvent.Phase.END) BDKeyBindings.handleTick();
            });
            MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
                        SuperbAmmoCache.clear();
                        NetworkItemCache.clear();
                        PrimaryNetClientStorage.INSTANCE.clear();
                        SyncConfigClient.reset();
                        SoulEnergyState.clear();
                        ManaState.clear();
                        if (ModList.get().isLoaded("tacz")) TaczAmmoCache.clear();
                    });
            // 存储界面工作台 UI：网络附魔台（神化）
            com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbenches.register(
                    com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget.ID,
                    com.solr98.beyondintegration.client.page.ApothEnchantTableWorkbench.INSTANCE);
            // 存储界面工作台 UI：网络附魔台（菜单侧类型在 BeyondIntegration.commonSetup 注册）
            com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbenches.register(
                    com.solr98.beyondintegration.feature.enchant.EnchantTableWidget.ID,
                    com.solr98.beyondintegration.client.page.EnchantTableWorkbench.INSTANCE);
            // 网络通道过滤界面（BD 0.8.3 未提供，BI 自带）
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.solr98.beyondintegration.init.ModMenus.NET_PATHWAY_FILTER.get(),
                    com.solr98.beyondintegration.client.gui.NetPathwayFilterScreen::new);
            // JEI 点击取物（Shift+左/右键从主网络取物；未装 JEI 时内部直接返回）
            JeiExtractInputHandler.register();
            // BD 界面页注入：网络喂食器口渴设置（BD 0.8 界面无此项，BI 以页签形式并列注入）
            registerBdPages();
            // 打开喂食器界面时向服务端要一次权威设置
            MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ScreenEvent.Init.Post e) -> {
                        if (e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                                && screen.getMenu() instanceof com.wintercogs.beyonddimensions.common.menu.NetFeederMenu) {
                            com.solr98.beyondintegration.client.FeederThirstClientState.reset();
                            com.solr98.beyondintegration.client.page.FeederThirstPage.INSTANCE.requestCurrent();
                        }
                    });
            // 打开经验棒界面时向服务端要一次修复设置（设置存在物品 NBT 上，客户端副本不可信）
            MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ScreenEvent.Init.Post e) -> {
                        if (e.getScreen() instanceof com.wintercogs.beyonddimensions.client.ui.machine.XpExchangeScreen) {
                            com.solr98.beyondintegration.client.XpRepairClientState.reset();
                            com.solr98.beyondintegration.client.page.XpRepairPage.INSTANCE.requestCurrent();
                        }
                    });
        });
        registerConfigScreen();
    }

    /**
     * 注册注入到 BD 界面的页（BdPage）。
     * <p>BD 0.8.3 的机器界面是 Kotlin `BdInventoryScreen`，官方扩展点就是 `BdPages.register`；
     * 这里用反射式延迟调用，未装 BD 0.8（无该类）时静默跳过。
     */
    private static void registerBdPages() {
        try {
            com.wintercogs.beyonddimensions.api.ui.page.BdPages.register(
                    new net.minecraft.resources.ResourceLocation(
                            BeyondIntegration.MODID, "feeder_thirst"),
                    com.wintercogs.beyonddimensions.common.menu.NetFeederMenu.class,
                    com.solr98.beyondintegration.client.page.FeederThirstPage.INSTANCE,
                    com.wintercogs.beyonddimensions.api.ui.page.BdPages.INSTANCE.last());
        } catch (Throwable ignored) {
            // BD 版本不含页面扩展点：不注入
        }
        try {
            // 经验棒「自动修复设置」页
            com.wintercogs.beyonddimensions.api.ui.page.BdPages.register(
                    new net.minecraft.resources.ResourceLocation(
                            BeyondIntegration.MODID, "xp_repair"),
                    com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu.class,
                    com.solr98.beyondintegration.client.page.XpRepairPage.INSTANCE,
                    com.wintercogs.beyonddimensions.api.ui.page.BdPages.INSTANCE.last());
        } catch (Throwable ignored) {
            // BD 版本不含页面扩展点：不注入
        }
        try {
            // 网络设置页：附魔分离 / 自动充电 / 网络药水护符
            // 挂 BD 存储界面（终端菜单 DimensionsNetTerminalMenu 继承 DimensionsNetMenu，自动生效）
            com.wintercogs.beyonddimensions.api.ui.page.BdPages.register(
                    new net.minecraft.resources.ResourceLocation(
                            BeyondIntegration.MODID, "net_settings"),
                    com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu.class,
                    com.solr98.beyondintegration.client.page.NetSettingsPage.INSTANCE,
                    com.wintercogs.beyonddimensions.api.ui.page.BdPages.INSTANCE.last());
        } catch (Throwable ignored) {
            // BD 版本不含页面扩展点：不注入
        }
        // 组件预览（调试页）：只在开发环境注册，避免发布版出现
        if (!net.minecraftforge.fml.loading.FMLEnvironment.production) {
            try {
                com.wintercogs.beyonddimensions.api.ui.page.BdPages.register(
                        new net.minecraft.resources.ResourceLocation(
                                BeyondIntegration.MODID, "component_gallery"),
                        com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu.class,
                        com.solr98.beyondintegration.client.page.ComponentGalleryPage.INSTANCE,
                        com.wintercogs.beyonddimensions.api.ui.page.BdPages.INSTANCE.last());
            } catch (Throwable ignored) {
                // BD 版本不含页面扩展点：不注入
            }
            try {
                com.wintercogs.beyonddimensions.api.ui.page.BdPages.register(
                        new net.minecraft.resources.ResourceLocation(
                                BeyondIntegration.MODID, "layout_gallery"),
                        com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu.class,
                        com.solr98.beyondintegration.client.page.LayoutGalleryPage.INSTANCE,
                        com.wintercogs.beyonddimensions.api.ui.page.BdPages.INSTANCE.last());
            } catch (Throwable ignored) {
                // BD 版本不含页面扩展点：不注入
            }
        }
        // 附魔合并（装备来自网络，服务端扫描附魔书并执行）
        // 界面已移除：待 BD 提供存储界面右栏扩展 API 后按“复刻存储界面 + 右侧扩展”重做，
        // 见 计划项目/待办/2026-10-30-附魔合并界面复刻存储界面右侧扩展.md
    }

    /**
     * 注册 Cloth Config 配置界面（仅在 cloth_config 已加载时生效）。
     * <p>刻意用反射式的延迟调用 + try/catch：未装 Cloth Config 时不得加载
     * {@code client.config.ModConfigScreen}（其类体会解析 clothconfig 类型）。
     */
    private static void registerConfigScreen() {
        if (!ModList.get().isLoaded("cloth_config")) return;
        try {
            net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (mc, parent) -> com.solr98.beyondintegration.client.config.ModConfigScreen
                                    .createScreen(parent)));
        } catch (Throwable ignored) {
            // Cloth Config 版本不兼容：保持无配置界面
        }
    }

    /** 注册 HUD 位置编辑器快捷键（受客户端配置 hud_visual_edit 控制是否可用）。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(BDKeyBindings.OPEN_SOUL_HUD_EDITOR);
    }

    /** 注册本模组 HUD 叠加层：快捷栏上方提示 / 主网络同步状态 / 网络魔力（铁魔法联动）。 */
    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "beyond_net_hint", new NetHintOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "beyond_net_sync_status", new NetSyncStatusOverlay());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "beyond_net_mana", NetManaHudOverlay.INSTANCE);
    }
}
