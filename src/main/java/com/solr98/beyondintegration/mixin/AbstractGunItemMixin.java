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

/**
 * 注入 TACZ 的 {@link AbstractGunItem}，扩展换弹与弹药判定：
 * 允许从 BeyondDimensions 维度网络中补充弹药（canReload / hasInventoryAmmo / findAndExtractInventoryAmmo），
 * 并在联网但无弹药时向玩家发送冷却限流的提示消息。
 */
@Mixin(value = AbstractGunItem.class, remap = false)
public class AbstractGunItemMixin {

    /** 玩家 UUID -> 上次无弹药提示时间，用于提示冷却 */
    private static final Map<UUID, Long> lastNotify = new HashMap<>();
    /** 无弹药提示冷却间隔（毫秒） */
    private static final long NOTIFY_COOLDOWN_MS = 5000;

    /** 扩展 canReload：背包无可装填弹药时，若网络中有弹药则允许换弹，否则提示玩家 */
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

    /** 扩展 hasInventoryAmmo：网络中存在弹药时视为拥有库存弹药（服务端按玩家网络、客户端按本地缓存判断） */
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

    /** 扩展 findAndExtractInventoryAmmo：背包弹药不足时，从玩家维度网络或终端容器网络中扣除弹药补足 */
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

    /** 向服务端玩家发送“网络无弹药”的系统消息，受 NOTIFY_COOLDOWN_MS 冷却限制 */
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
