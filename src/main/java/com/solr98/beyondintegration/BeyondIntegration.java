package com.solr98.beyondintegration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.PlayerNetworkSyncHandler;
import com.solr98.beyondintegration.handler.VehicleInteractHandler;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.solr98.beyondintegration.handler.BDNetworkHelper;
import com.solr98.beyondintegration.handler.NetworkNotificationTracker;
import com.solr98.beyondintegration.handler.NetworkSavedData;
import com.solr98.beyondintegration.handler.VehicleNetStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
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

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register server config
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CommandConfig.SERVER_SPEC);

        // Register Cloth Config screen (client-only)
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientRegistrar::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        registerItemBlacklistHandler();
        registerEnchantmentBookSeparator();
        registerAmmoBoxExtractHandler();
        registerSuperbAmmoInsertHandler();
        registerPlayerNetworkSyncHandler();
        registerVehicleInteractHandler();
        com.solr98.beyondintegration.network.PacketHandler.register();
    }

    private void registerEnchantmentBookSeparator() {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler());
        LOGGER.info("Registered EnchantmentBookSeparatorHandler");
    }

    private void registerItemBlacklistHandler() {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.handler.ItemBlacklistHandler());
        LOGGER.info("Registered ItemBlacklistHandler");
    }

    private void registerAmmoBoxExtractHandler() {
        if (ModList.get().isLoaded("tacz")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.handler.AmmoBoxExtractHandler());
            LOGGER.info("Registered AmmoBoxExtractHandler");
        }
    }

    private void registerSuperbAmmoInsertHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.handler.SuperbAmmoInsertHandler());
            LOGGER.info("Registered SuperbAmmoInsertHandler");
        }
    }

    private void registerPlayerNetworkSyncHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            MinecraftForge.EVENT_BUS.register(new PlayerNetworkSyncHandler());
            LOGGER.info("Registered PlayerNetworkSyncHandler");
        }
    }

    private void registerVehicleInteractHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            MinecraftForge.EVENT_BUS.register(new VehicleInteractHandler());
            LOGGER.info("Registered VehicleInteractHandler");
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
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
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BDNetworkHelper.clearCache(player.getUUID());
            NetworkNotificationTracker.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            NetworkSavedData.initialize(serverLevel);
        }
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            NetworkSavedData.markDirty();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            VehicleNetStorage.cleanupStale();
        }
    }

}
