package com.solr98.beyondintegration;

import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.ammo.sw.SwAmmoPollingService;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Method;

import net.neoforged.api.distmarker.Dist;

/**
 * Beyond Integration 模组主类（Forge 1.20.1）。
 * 负责模组初始化：注册菜单、通用配置、事件总线监听，
 * 以及按依赖模组（tacz / superbwarfare / beyonddimensions）条件注册
 * 各功能处理器（物品黑名单、弹药箱提取、SW 弹药轮询、载具绑定、哨戒炮绑定等）。
 */
// The value here should match an entry in the META-INF/mods.toml file
@Mod(BeyondIntegration.MODID)
public class BeyondIntegration {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "beyond_integration";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 模组构造器：注册 mod 总线事件、菜单、NeoForge 事件总线与通用配置 */
    public BeyondIntegration(IEventBus modEventBus, ModContainer modContainer) {

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register menu types

        // Register recipe serializers (furnace terminal smelt-all)
        com.solr98.beyondintegration.init.ModRecipes.register(modEventBus);

        // Register menu types（当前仅网络通道过滤界面）
        com.solr98.beyondintegration.init.ModMenus.register(modEventBus);

        // Register purified water fluids (dirty/slightly_dirty/acceptable/purified)
        com.solr98.beyondintegration.init.ModFluids.register(modEventBus);

        // Goety 仪式工厂（网络灵魂源激活仪式）：仅 Goety 加载时向 goety:ritual_factory 注册
        if (ModList.get().isLoaded("goety")) {
            com.solr98.beyondintegration.feature.soul.ModRitualFactories.RITUALS.register(modEventBus);
        }

        // Register ourselves for server and other game events we are interested in
        NeoForge.EVENT_BUS.register(this);

        // Register common config (synced to client)
        modContainer.registerConfig(ModConfig.Type.COMMON, CommandConfig.SERVER_SPEC);
        // Register client-only config (GUI preferences, e.g. TACZ smith table network mode)
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_SPEC);

        // Register Cloth Config screen (client-only)

        // 配置文件注释统一：监听配置加载/重载缓存 COMMON 配置，加载完成后按需重写
        modEventBus.addListener(this::onModConfigLoading);
        modEventBus.addListener(this::onModConfigReloading);
        modEventBus.addListener(this::onLoadComplete);
    }

    /** Goety 类是否存在（避免直接引用导致版本不匹配时 NoClassDefFoundError）。 */
    private static boolean goetyClassAvailable(String name) {
        try {
            Class.forName(name, false, BeyondIntegration.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** common 阶段初始化：按依赖条件注册全部功能处理器与网络包 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册充电平台实现（能量 capability / 模组判定 / 属性判定）
        com.solr98.beyondintegration.core.energy.ChargePlatform.set(
                new com.solr98.beyondintegration.core.energy.NeoChargePlatform());
        // 注册网络灵魂能量键（必须在任何网络存储反序列化之前，且幂等）
        com.solr98.beyondintegration.feature.soul.SoulKeyRegistration.register();
        // 存储界面工作台：网络附魔台（神化规则）
        com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes.register(
                com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget.ID,
                com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchType.of(
                        menu -> new com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget(menu, menu.storage)));
        // 存储界面工作台：网络附魔台（普通规则；菜单侧类型 + 客户端见 ClientSetup）
        com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes.register(
                com.solr98.beyondintegration.feature.enchant.EnchantTableWidget.ID,
                com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchType.of(
                        menu -> new com.solr98.beyondintegration.feature.enchant.EnchantTableWidget(menu, menu.storage)));
        // 注册网络魔力键（Iron's Spells 联动；同样必须在任何网络存储反序列化之前，且幂等）
        com.solr98.beyondintegration.feature.mana.ManaKeyRegistration.register();
        // 网络独立回蓝改为 BD 网络 tick 任务（NetTickTasks）
        com.solr98.beyondintegration.feature.mana.ManaNetRegenTask.register();
        // 注册网络女仆 P 点键（TLM 联动；同样必须在任何网络存储反序列化之前，且幂等）
        // 注意：P 点功能未完成，本次发布禁用（行为入口见下方 maidPower*Handler 与 AltarPowerConsumeMixin，
        // 已在 mixins.json 移除）；键仍注册以保证旧存档中已存在的 maid_power 数据能反序列化。
        com.solr98.beyondintegration.feature.maidpower.MaidPowerKeyRegistration.register();
        // 网络魔力源：施法消耗（优先玩家、缺口网络）+ 回复沉淀（仅 irons_spellbooks 加载时注册）
        if (ModList.get().isLoaded("irons_spellbooks")) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.mana.IronsManaHandler());
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.mana.ManaHudSyncHandler());
                LOGGER.info("Registered IronsManaHandler (Iron's Spells network mana source)");
        }
        // 网络灵魂源：击杀沉淀监听（仅 Goety 加载且提供对应 API 时）
        if (ModList.get().isLoaded("goety")) {
            // Goety 版本差异保护：注册前按“类是否存在”门控，避免 EventBus 反射方法签名时
            // 因缺失的 Goety 类抛 NoClassDefFoundError，使本模组进入 Forge broken state。
            if (goetyClassAvailable("com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent$Gain")) {
                NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.soul.SoulGainHandler());
            } else {
                LOGGER.warn("[BI] Goety ChangeSoulEnergyEvent$Gain not found; soul-gain sync disabled (Goety version mismatch?)");
            }
            if (goetyClassAvailable("com.Polarice3.Goety.utils.SEHelper")) {
                // HUD：订阅主网络存储增量，仅灵魂变化时增量推送（由 SEUpdatePacketMixin 把网络魂并入原版条）
                NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.handler.SoulHudSyncHandler());
            } else {
                LOGGER.warn("[BI] Goety SEHelper not found; soul HUD sync disabled (Goety version mismatch?)");
            }
            // 灵魂源激活仪式的收尾动画调度（动画结束后才正式激活）
            NeoForge.EVENT_BUS.addListener(com.solr98.beyondintegration.feature.soul.SoulRitualAnimation::onServerTick);
        }
        registerItemBlacklistHandler();
        registerEnchantmentBookSeparator();
        registerBucketSeparator();
        registerAmmoBoxExtractHandler();
        registerSuperbAmmoInsertHandler();
        registerSwAmmoPollingService();
        // 通用装备位充电与网络药水护符改为 BD 网络 tick 任务（NetTickTasks）
        com.solr98.beyondintegration.feature.ammo.sw.EnergyChargeTask.register();
        com.solr98.beyondintegration.feature.charm.PotionCharmTask.register();
        // 网络药水护符缓存清理（网络销毁 / 服务器停止）
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent stopEvent) ->
                com.solr98.beyondintegration.feature.charm.NetworkPotionCharmHandler.clearCaches());
        NeoForge.EVENT_BUS.addListener(
                (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed destroyedEvent) ->
                        com.solr98.beyondintegration.feature.charm.NetworkPotionCharmHandler
                                .onNetDestroyed(destroyedEvent.getDestroyedId()));
        // 终端绑定统一分发：方块=左键（哨戒炮等）、实体=右键（sbw 载具等）
        com.solr98.beyondintegration.feature.terminal.TerminalBindingHub.registerBlock(
                new com.solr98.beyondintegration.feature.terminal.SentryBindTarget());
        if (ModList.get().isLoaded("superbwarfare")) {
            com.solr98.beyondintegration.feature.terminal.TerminalBindingHub.registerEntity(
                    new com.solr98.beyondintegration.feature.terminal.VehicleBindTarget());
        }
        NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.AutoTotemHandler());
        NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.TotemBurstTicker());
        // setHealth 复活实现（事件版 + Mixin 版）：配置关闭时不注册处理器（类不加载，零事件开销）
        if (CommandConfig.reviveEventEnabled() || CommandConfig.reviveMixinEnabled()) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.revive.ReviveFingerprintHandler());
            LOGGER.info("Registered ReviveFingerprintHandler");
        }
        if (CommandConfig.reviveEventEnabled()) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.revive.SetHealthReviveHandler());
            LOGGER.info("Registered SetHealthReviveHandler (event)");
        }
        if (ModList.get().isLoaded("touhou_little_maid")) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.MaidAutoTotemHandler());
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.maid.MaidEnergyChargeHandler());
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.maid.MaidPotionCharmHandler());
            // 女仆 P 点：拾取入网 + 玩家 P 点网络回填
            // 功能未完成，本次发布禁用（祭坛消耗扣网络见 AltarPowerConsumeMixin，已从 mixins.json 移除）
            // NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.maid.MaidPowerPickupHandler());
            // NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.maid.MaidPowerRefillHandler());
            LOGGER.info("Registered MaidAutoTotemHandler");
        }
        // 网络包由 PacketHandler 的 @EventBusSubscriber(RegisterPayloadHandlersEvent) 自动注册
        registerTaczTrackerDrain();

        // 打开 BD 菜单时补一次 dataSlot 全量推送。
        // vanilla 的 ServerPlayer.initMenu 只挂监听器/同步器，不调 sendAllDataToRemote()；
        // 而 synchronizeDataSlotToRemote 只推「相对 remoteDataSlots（初始全 0）发生变化」的值，
        // 所以界面的初始自定义数据（附魔台属性/上限等）在打开时到不了客户端。
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open e) -> {
                    if (e.getContainer() instanceof com.wintercogs.beyonddimensions.common.menu.BDBaseMenu) {
                        e.getContainer().sendAllDataToRemote();
                    }
                });
        registerSubscriptionHubCleanup();
    }

    /** 缓存的 COMMON 配置对象（供加载完成后统一注释使用）。 */
    private net.neoforged.fml.config.ModConfig commonConfig;
    /** 缓存的 CLIENT 配置对象（供加载完成后统一注释使用）。 */
    private net.neoforged.fml.config.ModConfig clientConfig;

    /** 配置加载时记录 COMMON/CLIENT 配置实例。 */
    private void onModConfigLoading(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        trackConfig(event.getConfig());
    }

    /** 配置重载时记录 COMMON/CLIENT 配置实例。 */
    private void onModConfigReloading(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        trackConfig(event.getConfig());
        // 主网络同步配置变更：向在线玩家重推配置，并重置会话基线下一 tick 按新范围全量重推
        if (MODID.equals(event.getConfig().getModId())) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                com.solr98.beyondintegration.handler.NetworkSyncConfigPusher.pushAll(server);
                com.solr98.beyondintegration.network.PrimaryNetSyncManager.onConfigChanged();
            }
        }
        // 灵魂改用 RI 键：配置重载时迁移旧数据（所有 BD 网络，服务端线程执行，幂等）
        if (MODID.equals(event.getConfig().getModId())
                && event.getConfig().getType() == net.neoforged.fml.config.ModConfig.Type.COMMON
                && com.solr98.beyondintegration.CommandConfig.soulUseRiKey()) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> com.solr98.beyondintegration.feature.soul.SoulKeyMigration
                        .migrateIfEnabled(server));
            }
        }
    }

    /** 服务器启动后：若灵魂已配置为 RI 键，执行一次幂等迁移；魔力旧键也在此迁移。 */
    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        com.solr98.beyondintegration.feature.mana.ManaNetRegenTask.setServer(event.getServer());
        com.solr98.beyondintegration.feature.ammo.sw.EnergyChargeTask.setServer(event.getServer());
        com.solr98.beyondintegration.feature.charm.PotionCharmTask.setServer(event.getServer());
        com.solr98.beyondintegration.feature.soul.SoulKeyMigration.migrateIfEnabled(event.getServer());
        com.solr98.beyondintegration.feature.mana.ManaKeyMigration.migrate(event.getServer());
        // SW 旧版虚拟弹药 → 散装弹药物品（存档已加载、BD 网络可读写时执行一次）
        com.solr98.beyondintegration.feature.ammo.sw.SwAmmoMigration.migrate(event.getServer());
        // 存量水表示归一：带纯度的原版水 → BI 4 档流体（幂等）
        com.solr98.beyondintegration.feature.feeder.WaterPurityMigration.migrate(event.getServer());
    }

    private void trackConfig(net.neoforged.fml.config.ModConfig config) {
        if (!MODID.equals(config.getModId())) {
            return;
        }
        if (config.getType() == net.neoforged.fml.config.ModConfig.Type.COMMON) {
            this.commonConfig = config;
        } else if (config.getType() == net.neoforged.fml.config.ModConfig.Type.CLIENT) {
            this.clientConfig = config;
        }
    }

    /** 模组加载完成后：COMMON/CLIENT 配置注释语言与 command_language 不一致时重写（值保持不变）。 */
    private void onLoadComplete(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        // RI 缺席时补注册灵魂兜底键（同 RI ID），保证已迁移数据可反序列化
        com.solr98.beyondintegration.feature.soul.SoulKeyRegistration.ensureRiKeyFallback();
        com.solr98.beyondintegration.core.config.ConfigCommentLang.saveIfLanguageChanged(this.commonConfig);
        com.solr98.beyondintegration.core.config.ConfigCommentLang.saveIfLanguageChanged(this.clientConfig);
        // BD 能力暴露包装：网络通道白名单过滤（BD 0.8 统一走 CapabilityHelper.USHandlerMap）
        com.solr98.beyondintegration.handler.NetPathwayCapabilityWrapper.install();
    }

    /** 注册统一订阅中心的全局清理（网络销毁 / 服务器停止时批量退订全部 BD 订阅） */
    private void registerSubscriptionHubCleanup() {
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub.clearAll();
                com.solr98.beyondintegration.feature.mana.ManaNetRegenTask.setServer(null);
                com.solr98.beyondintegration.feature.ammo.sw.EnergyChargeTask.setServer(null);
                com.solr98.beyondintegration.feature.charm.PotionCharmTask.setServer(null);
            }
        );
        // 服务器停止时显式落盘 beyond_integration_attachments.dat（NetworkAmmoData 持久化）
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                com.solr98.beyondintegration.handler.NetworkDataStore.saveNow();
            }
        );
        // 服务器停止：清空主网络同步会话与限流状态
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppingEvent event) ->
                com.solr98.beyondintegration.network.PrimaryNetSyncManager.clear()
        );
        NeoForge.EVENT_BUS.addListener(
            (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) -> {
                com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub.onNetDestroyed(event.getDestroyedId());
                // 仪式结构方块网络清单缓存：网络销毁后失效
                com.solr98.beyondintegration.handler.RitualNetworkStructure.clear(event.getDestroyedId());
            }
        );
        // 服务器停止：清空仪式结构方块的网络清单缓存
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppingEvent event) ->
                com.solr98.beyondintegration.handler.RitualNetworkStructure.clear()
        );
        LOGGER.info("Registered BdSubscriptionHub cleanup");
    }

    /** 注册附魔分离插入拦截器（多附魔书/附魔物品进网络时自动分离） */
    private void registerEnchantmentBookSeparator() {
        com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.register();
        LOGGER.info("Registered EnchantmentBookSeparatorHandler");
    }

    /** 注册桶入网自动分离拦截器（含流体的桶拆为"流体 + 空容器"分别入网） */
    private void registerBucketSeparator() {
        com.solr98.beyondintegration.handler.BucketSeparatorHandler.register();
        LOGGER.info("Registered BucketSeparatorHandler");
    }

    /** 注册物品黑名单插入拦截器 */
    private void registerItemBlacklistHandler() {
        com.solr98.beyondintegration.feature.blacklist.ItemBlacklistHandler.register();
        LOGGER.info("Registered ItemBlacklistHandler");
    }

    /** 注册 TACZ 弹药箱/弹药库方块自动提取拦截器（仅在 tacz 加载时） */
    private void registerAmmoBoxExtractHandler() {
        if (ModList.get().isLoaded("tacz")) {
            com.solr98.beyondintegration.feature.ammo.tacz.AmmoBoxExtractHandler.register();
            // 1.2.0 弹药库方块：入网时拆解内部弹药，空格入库
            com.solr98.beyondintegration.feature.ammo.tacz.AmmoStorageExtractHandler.register();
            LOGGER.info("Registered AmmoBoxExtractHandler");
        }
    }

    /** 注册 SW 弹药插入拦截器（仅在 superbwarfare 加载时） */
    private void registerSuperbAmmoInsertHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            com.solr98.beyondintegration.feature.ammo.sw.SuperbAmmoInsertHandler.register();
            LOGGER.info("Registered SuperbAmmoInsertHandler");
        }
    }

    /**
     * 注册 SW 虚拟弹药轮询服务：每间隔推送差异，并在
     * 服务器停止 / 网络销毁时清理弹药追踪数据。
     */
    private void registerSwAmmoPollingService() {
        if (ModList.get().isLoaded("superbwarfare")) {
            NeoForge.EVENT_BUS.register(new SwAmmoPollingService());
            NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                    com.solr98.beyondintegration.feature.ammo.sw.SwAmmoTracker.clear();
                }
            );
            NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() != null) {
                        com.solr98.beyondintegration.feature.ammo.sw.SwAmmoPollingService
                                .onPlayerLoggedOut(event.getEntity().getUUID());
                    }
                }
            );
            NeoForge.EVENT_BUS.addListener(
                (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) -> {
                    com.solr98.beyondintegration.feature.ammo.sw.SwAmmoTracker.removeById(event.getDestroyedId());
                }
            );
            LOGGER.info("Registered SwAmmoPollingService");
        }
    }

    /**
     * 注册 TACZ 弹药追踪服务：周期性扫描网络弹药并推送，
     * 并在服务器停止 / 网络销毁 / 玩家登出时清理对应追踪数据。
     */
    private void registerTaczTrackerDrain() {
        if (ModList.get().isLoaded("tacz")) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoPollingService());
            NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoTracker.clear();
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoIndex.clear();
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoPollingService.clear();
                    com.solr98.beyondintegration.feature.ammo.tacz.PlayerNetUsageTracker.clear();
                }
            );
            NeoForge.EVENT_BUS.addListener(
                (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) -> {
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoTracker.removeById(event.getDestroyedId());
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoIndex.removeById(event.getDestroyedId());
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoPollingService.onNetworkDestroyed(
                            event.getDestroyedId(),
                            net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer());
                    NetworkAmmoData.remove(event.getDestroyedId());
                }
            );
            NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() != null) {
                        com.solr98.beyondintegration.feature.ammo.tacz.PlayerNetUsageTracker.remove(event.getEntity().getUUID());
                    }
                }
            );
            LOGGER.info("Registered TaczAmmoPollingService");
        }
    }

    /** 主世界加载时初始化网络弹药持久化数据（先兼容迁移旧存档，再按统一键名加载） */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            com.solr98.beyondintegration.handler.NetworkDataStore.migrateLegacy(serverLevel.getServer());
            NetworkAmmoData.initialize(serverLevel);
        }
    }

    /** 主世界保存时标记网络弹药数据为待保存 */
    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            NetworkAmmoData.markDirty();
        }
    }

}
