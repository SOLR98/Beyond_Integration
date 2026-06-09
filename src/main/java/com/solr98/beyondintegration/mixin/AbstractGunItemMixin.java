package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.cache.AmmoCountCache;
import com.solr98.beyondintegration.handler.NetworkAmmoExtractor;
import com.solr98.beyondintegration.handler.NetworkAmmoHandler;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(value = AbstractGunItem.class, remap = false)
public class AbstractGunItemMixin {

    private static final Map<UUID, Long> lastNotify = new HashMap<>();
    private static final long NOTIFY_COOLDOWN_MS = 5000;

    @Unique
    private static final ThreadLocal<ServerPlayer> beyond$reloadingPlayer = new ThreadLocal<>();

    @Inject(method = "canReload", at = @At("RETURN"), cancellable = true)
    private void onCanReload(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || iGun.useInventoryAmmo(gunItem)) return;

        if (shooter instanceof ServerPlayer sp) {
            if (NetworkAmmoHandler.hasNetworkAmmo(sp, gunItem)) {
                beyond$reloadingPlayer.set(sp);
                cir.setReturnValue(true);
            } else {
                sendNotify(shooter);
            }
        } else if (!shooter.level().isClientSide) {
            if (NetworkAmmoHandler.hasNetworkAmmo(shooter, gunItem)) {
                cir.setReturnValue(true);
            }
        } else {
            ResourceLocation ammoId = NetworkAmmoExtractor.getAmmoIdClient(gunItem);
            if (ammoId != null && AmmoCountCache.hasData(ammoId) && AmmoCountCache.getCount(ammoId) > 0) {
                cir.setReturnValue(true);
            } else if (ammoId != null && !AmmoCountCache.hasData(ammoId)) {
                AmmoCountCache.requestQuick(ammoId);
            }
        }
    }

    @Inject(method = "hasInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void onHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.useInventoryAmmo(gun)) return;

        if (shooter instanceof ServerPlayer sp) {
            if (NetworkAmmoHandler.hasNetworkAmmo(sp, gun)) {
                beyond$reloadingPlayer.set(sp);
                cir.setReturnValue(true);
            }
        } else if (!shooter.level().isClientSide) {
            if (NetworkAmmoHandler.hasNetworkAmmo(shooter, gun)) {
                cir.setReturnValue(true);
            }
        } else {
            ResourceLocation ammoId = NetworkAmmoExtractor.getAmmoIdClient(gun);
            if (ammoId != null && AmmoCountCache.hasData(ammoId) && AmmoCountCache.getCount(ammoId) > 0) {
                cir.setReturnValue(true);
            } else if (ammoId != null && !AmmoCountCache.hasData(ammoId)) {
                AmmoCountCache.requestQuick(ammoId);
            }
        }
    }

    @Inject(method = "findAndExtractInventoryAmmo", at = @At("HEAD"), cancellable = true)
    private void onFindAndExtractInventoryAmmo(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                CallbackInfoReturnable<Integer> cir) {
        int fromNet = 0;

        if (itemHandler instanceof net.minecraftforge.items.wrapper.PlayerMainInvWrapper w) {
            var player = w.getInventoryPlayer().player;
            if (player instanceof ServerPlayer sp) {
                fromNet = NetworkAmmoHandler.consumeFromNetwork(sp, gunItem, needAmmoCount);
            }
        }

        if (fromNet <= 0) {
            ServerPlayer sp = beyond$reloadingPlayer.get();
            if (sp != null) {
                fromNet = NetworkAmmoHandler.consumeFromNetwork(sp, gunItem, needAmmoCount);
            }
        }

        if (fromNet <= 0 && net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid")) {
            try {
                Class<?> maidInvClass = Class.forName("com.github.tartaricacid.touhoulittlemaid.inventory.handler.MaidInvWrapper");
                if (maidInvClass.isInstance(itemHandler)) {
                    Object maid = maidInvClass.getMethod("getMaid").invoke(itemHandler);
                    if (maid instanceof net.minecraft.world.entity.LivingEntity livingMaid) {
                        var maidNet = com.solr98.beyondintegration.maid.MaidNetworkHelper.findTerminal(livingMaid);
                        if (maidNet != null) {
                            fromNet = com.solr98.beyondintegration.handler.NetworkAmmoExtractor.consumeAmmoDirectly(gunItem, needAmmoCount, maidNet);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (fromNet <= 0) {
            DimensionsNet terminalNet = NetworkAmmoHandler.findTerminalInHandler(itemHandler);
            if (terminalNet != null) {
                fromNet = com.solr98.beyondintegration.handler.NetworkAmmoExtractor.consumeAmmoDirectly(gunItem, needAmmoCount, terminalNet);
            }
        }

        if (fromNet > 0) {
            cir.setReturnValue(fromNet);
        }
        beyond$reloadingPlayer.remove();
    }

    private static void sendNotify(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) return;
        long now = System.currentTimeMillis();
        if (now - lastNotify.getOrDefault(player.getUUID(), 0L) < NOTIFY_COOLDOWN_MS) return;
        lastNotify.put(player.getUUID(), now);
        var net = com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null) {
            player.sendSystemMessage(
                    Component.translatable("message.beyond_integration.network_no_ammo", net.getId()));
        }
    }
}
