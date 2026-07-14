package com.solr98.beyondintegration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.ammo.sw.SwPlayerAmmoSyncer;
import com.solr98.beyondintegration.feature.vehicle.VehicleInteractHandler;
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

import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.MeterSnapshotTicker;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.solr98.beyondintegration.handler.AuditInspectHandler;
import com.solr98.beyondintegration.handler.GuiAuditHandler;
import com.solr98.beyondintegration.handler.PlayerInspectData;
import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

import com.solr98.beyondintegration.client.ClientRegistrar;
import com.solr98.beyondintegration.feature.conversion.ConversionLoader;
import com.solr98.beyondintegration.feature.conversion.RecipeConversionHandler;
import com.solr98.beyondintegration.feature.extract.ExtractHandlerRegistry;
import com.solr98.beyondintegration.init.ModMenus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(BeyondIntegration.MODID)
public class BeyondIntegration {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "beyond_integration";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public BeyondIntegration() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register menu types
        ModMenus.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register datapack reload listener for conversion recipes
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.AddReloadListenerEvent event) -> {
            event.addListener(new ConversionLoader());
        });

        // Register common config (synced to client)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommandConfig.SERVER_SPEC);

        // Register Cloth Config screen (client-only)
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientRegistrar::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        registerItemBlacklistHandler();
        registerEnchantmentBookSeparator();
        registerAmmoBoxExtractHandler();
        registerSuperbAmmoInsertHandler();
        registerSuperbAmmoExtractHandler();
        registerConversionHandler();
        registerPlayerNetworkSyncHandler();
        registerVehicleInteractHandler();
        registerAuditInspectHandler();
        com.solr98.beyondintegration.network.PacketHandler.register();
        registerMeterSnapshotTicker();
        registerGuiAuditHandler();
        registerTaczTrackerDrain();
    }

    private void registerEnchantmentBookSeparator() {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler());
        LOGGER.info("Registered EnchantmentBookSeparatorHandler");
    }

    private void registerItemBlacklistHandler() {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.feature.blacklist.ItemBlacklistHandler());
        LOGGER.info("Registered ItemBlacklistHandler");
    }

    private void registerAmmoBoxExtractHandler() {
        if (ModList.get().isLoaded("tacz")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.feature.ammo.tacz.AmmoBoxExtractHandler());
            LOGGER.info("Registered AmmoBoxExtractHandler");
        }
    }

    private void registerSuperbAmmoInsertHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.feature.ammo.sw.SuperbAmmoInsertHandler());
            LOGGER.info("Registered SuperbAmmoInsertHandler");
        }
    }

    private void registerSuperbAmmoExtractHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            ExtractHandlerRegistry.register(new com.solr98.beyondintegration.feature.extract.sw.SuperbAmmoExtractHandler());
            LOGGER.info("Registered SuperbAmmoExtractHandler");
        }
    }

    private void registerConversionHandler() {
        ExtractHandlerRegistry.register(new RecipeConversionHandler());
        LOGGER.info("Registered RecipeConversionHandler");
    }

    private void registerPlayerNetworkSyncHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            MinecraftForge.EVENT_BUS.register(new SwPlayerAmmoSyncer());
            LOGGER.info("Registered SwPlayerAmmoSyncer");
        }
    }

    private void registerVehicleInteractHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            MinecraftForge.EVENT_BUS.register(new VehicleInteractHandler());
            LOGGER.info("Registered VehicleInteractHandler");
        }
    }

    private void registerAuditInspectHandler() {
        MinecraftForge.EVENT_BUS.register(new AuditInspectHandler());
        LOGGER.info("Registered AuditInspectHandler");
    }

    private void registerGuiAuditHandler() {
        if (CommandConfig.enableAuditLog()) {
            MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.handler.GuiAuditHandler());
            LOGGER.info("Registered GuiAuditHandler");
        }
    }

    private void registerMeterSnapshotTicker() {
        if (CommandConfig.enableAuditLog()) {
            MeterSnapshotTicker.register();
            LOGGER.info("Registered MeterSnapshotTicker");
        }
    }

    private void registerTaczTrackerDrain() {
        if (ModList.get().isLoaded("tacz")) {
            MinecraftForge.EVENT_BUS.addListener(
                (TickEvent.ServerTickEvent event) -> {
                    if (event.phase == TickEvent.Phase.END) {
                        com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoTracker.drainAllPending();
                    }
                }
            );
            LOGGER.info("Registered TaczAmmoTracker drain");
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null || !"sentrymechanicalarm".equals(blockId.getNamespace())) return;

        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.isEmpty() || NetedItem.getNetId(stack) < 0) return;

        event.setCanceled(true);

        if (event.getSide().isClient()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        if (be instanceof SentryNetIdAccessor accessor) {
            accessor.setSentryNetId(NetedItem.getNetId(stack));
        }

        try {
            Method getHeld = be.getClass().getMethod("getHeldItem");
            ItemStack held = (ItemStack) getHeld.invoke(be);
            if (held.isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_no_gun"), true);
                return;
            }

            Method addBox = be.getClass().getMethod("addAmmoBox", ItemStack.class);
            boolean ok = (boolean) addBox.invoke(be, stack);
            if (ok) {
                if (!player.isCreative()) stack.shrink(1);
                int boundNetId = NetedItem.getNetId(stack);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_net_bound", boundNetId),
                        true);
                if (CommandConfig.enableAuditLog()) {
                    String sentryDisplay = level.getBlockState(pos).getBlock().getName().getString();
                    BindingAuditLog.log(AuditEntry.bind(
                            player.getName().getString(), player.getUUID(),
                            boundNetId, "SENTRY", pos.toShortString(), sentryDisplay));
                    NetworkBindingRegistry.recordSentryBind(boundNetId, pos,
                            player.getName().getString(), player.getUUID(), sentryDisplay);
                }
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // BDNetworkHelper and NetworkNotificationTracker removed - caches are client-side
        }
    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            NetworkAmmoData.initialize(serverLevel);
            if (CommandConfig.enableTokenSystem()) {
                BindingTokenManager.initialize(serverLevel);
                NetworkMeters.initialize(serverLevel);
                PlayerInspectData.initialize(serverLevel);
                if (CommandConfig.enableAuditLog()) {
                    NetworkBindingRegistry.initialize(serverLevel);
                    BindingAuditLog.initialize(serverLevel);
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            NetworkAmmoData.markDirty();
            if (CommandConfig.enableAuditLog()) {
                NetworkMeters.markDirty();
                BindingAuditLog.flush();
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            VehicleNetStorage.cleanupStale();
            if (CommandConfig.enableAuditLog()) {
                BindingAuditLog.close();
            }
        }
    }

}
