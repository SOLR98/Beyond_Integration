package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.feature.ammo.tacz.NetworkAmmoHandler;
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

    @Inject(method = "canReload", at = @At("RETURN"), cancellable = true)
    private void onCanReload(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        if (shooter instanceof ServerPlayer sp) {
            if (NetworkAmmoHandler.hasNetworkAmmo(sp, gunItem)) {
                cir.setReturnValue(true);
            } else {
                sendNotify(shooter);
            }
        } else if (!shooter.level().isClientSide) {
            if (NetworkAmmoHandler.hasNetworkAmmo(shooter, gunItem)) {
                cir.setReturnValue(true);
            }
        } else {
            ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gunItem);
            if (ammoId == null) return;
            if (TaczAmmoCache.hasData(ammoId)) {
                if (TaczAmmoCache.getCount(ammoId) > 0) {
                    cir.setReturnValue(true);
                }
            } else {
                TaczAmmoCache.requestQuick(ammoId);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "hasInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void onHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return;

        if (shooter instanceof ServerPlayer sp) {
            if (NetworkAmmoHandler.hasNetworkAmmo(sp, gun)) {
                cir.setReturnValue(true);
            }
        } else if (!shooter.level().isClientSide) {
            if (NetworkAmmoHandler.hasNetworkAmmo(shooter, gun)) {
                cir.setReturnValue(true);
            }
        } else {
            ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gun);
            if (ammoId == null) return;
            if (TaczAmmoCache.hasData(ammoId)) {
                if (TaczAmmoCache.getCount(ammoId) > 0) {
                    cir.setReturnValue(true);
                }
            } else {
                TaczAmmoCache.requestQuick(ammoId);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "findAndExtractInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void onFindAndExtractInventoryAmmo(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                CallbackInfoReturnable<Integer> cir) {
        int fromOrig = cir.getReturnValueI();
        if (fromOrig >= needAmmoCount) return;
        int stillNeed = needAmmoCount - fromOrig;

        if (itemHandler instanceof net.minecraftforge.items.wrapper.PlayerMainInvWrapper w) {
            var player = w.getInventoryPlayer().player;
            if (player instanceof ServerPlayer sp) {
                int fromNet = com.solr98.beyondintegration.feature.ammo.tacz.NetworkAwareAmmoHandler
                        .consumeFromNetworks(sp, gunItem, stillNeed);
                if (fromNet > 0) {
                    cir.setReturnValue(fromOrig + fromNet);
                    return;
                }
            }
        }

        DimensionsNet terminalNet = NetworkAmmoHandler.findTerminalInHandler(itemHandler);
        if (terminalNet != null) {
            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, terminalNet);
            if (fromNet > 0) {
                cir.setReturnValue(fromOrig + fromNet);
            }
        }
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
