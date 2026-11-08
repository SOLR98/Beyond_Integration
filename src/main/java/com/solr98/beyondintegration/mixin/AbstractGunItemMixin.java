package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.feature.ammo.tacz.NetworkAmmoHandler;
import com.solr98.beyondintegration.feature.ammo.tacz.NetworkAwareAmmoHandler;
import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
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
 *
 * <p>priority = 100（低于 TUD 默认 1000）：Tacz-Unidict 以 TAIL 注入
 * findAndExtractInventoryAmmo 并改写返回值（$ 物品弹药场景），本模组以 RETURN 注入
 * 读取返回值作网络补弹基线——低 priority 保证 TUD 先执行、本模组后读取，
 * 避免已扣的网络弹药返回值被覆盖。
 */
@Mixin(value = AbstractGunItem.class, priority = 100, remap = false)
public class AbstractGunItemMixin {

    /** 玩家 UUID -> 上次无弹药提示时间，用于提示冷却 */
    private static final Map<UUID, Long> lastNotify = new HashMap<>();
    /** 无弹药提示冷却间隔（毫秒） */
    private static final long NOTIFY_COOLDOWN_MS = 5000;

    /**
     * 当前调用链的射击者：{@code canReload / hasInventoryAmmo} 带 shooter 参数，
     * 而实际扣弹的 {@code findAndExtractInventoryAmmos} 没有；同一线程内记录后供扣弹使用，
     * 覆盖女仆（非玩家实体）与哨戒（FakePlayer）的网络弹药路径。
     */
    @Unique
    private static final ThreadLocal<LivingEntity> beyond$currentShooter = new ThreadLocal<>();

    /** 扩展 canReload：背包无可装填弹药时，若网络中有弹药则允许换弹，否则提示玩家 */
    @Inject(method = "canReload", at = @At("RETURN"), cancellable = true)
    private void onCanReload(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        beyond$currentShooter.set(shooter);
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
            }
        }
    }

    /** 扩展 hasInventoryAmmo：网络中存在弹药时视为拥有库存弹药（服务端按玩家网络、客户端按本地缓存判断） */
    @Inject(method = "hasInventoryAmmo", at = @At("RETURN"), cancellable = true, require = 0)
    private void onHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo,
                                    CallbackInfoReturnable<Boolean> cir) {
        beyond$currentShooter.set(shooter);
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
            }
        }
    }

    /**
     * 扩展 findAndExtractInventoryAmmos（复数，TACZ 1.1.4 及旧 API）：背包弹药不足时补足网络弹药。
     * <p>TACZ 1.1.5+ 的射击/换弹实际调用单数版 {@code findAndExtractInventoryAmmo}，
     * 因此同时注入 {@link #onFindAndExtractInventoryAmmoSingle}。
     * <ul>
     *   <li>哨戒（FakePlayer）：按 {@link SentryFakePlayerNetMarker} 的绑定网络扣弹；</li>
     *   <li>玩家：主网络扣弹；</li>
     *   <li>女仆等非玩家实体：用 {@link #beyond$currentShooter} 记录的战斗实体走
     *       {@link NetworkAmmoHandler#consumeFromNetwork}（终端/女仆终端）；</li>
     *   <li>兜底：handler 内携带终端物品的网络。</li>
     * </ul>
     */
    @Inject(method = "findAndExtractInventoryAmmos", at = @At("RETURN"), cancellable = true, require = 0)
    private void onFindAndExtractInventoryAmmos(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                 CallbackInfoReturnable<Integer> cir) {
        beyond$refillFromNetwork(itemHandler, gunItem, needAmmoCount, cir);
    }

    /** 单数版（TACZ 1.1.5+）：真实射击/换弹扣弹路径 */
    @Inject(method = "findAndExtractInventoryAmmo", at = @At("RETURN"), cancellable = true, require = 0)
    private void onFindAndExtractInventoryAmmoSingle(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                     CallbackInfoReturnable<Integer> cir) {
        beyond$refillFromNetwork(itemHandler, gunItem, needAmmoCount, cir);
    }

    @Unique
    private static void beyond$refillFromNetwork(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount,
                                                 CallbackInfoReturnable<Integer> cir) {
        int fromOrig = cir.getReturnValueI();
        if (fromOrig >= needAmmoCount) {
            beyond$currentShooter.remove();
            return;
        }
        int stillNeed = needAmmoCount - fromOrig;
        LivingEntity shooter = beyond$currentShooter.get();
        try {
            // 1) 哨戒 fake player / 玩家：handler 是 PlayerMainInvWrapper
            if (itemHandler instanceof PlayerMainInvWrapper w) {
                Player owner = w.getInventoryPlayer().player;
                if (owner instanceof FakePlayer fp) {
                    // 哨戒：fake player 没有主网络，改用绑定的网络
                    int netId = SentryFakePlayerNetMarker.getNetId(fp);
                    if (netId >= 0) {
                        DimensionsNet net = DimensionsNet.getNetFromId(netId);
                        if (net != null) {
                            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
                            if (fromNet > 0) {
                                cir.setReturnValue(fromOrig + fromNet);
                                return;
                            }
                        }
                    }
                } else if (owner instanceof ServerPlayer sp) {
                    int fromNet = NetworkAwareAmmoHandler.consumeFromNetworks(sp, gunItem, stillNeed);
                    if (fromNet > 0) {
                        cir.setReturnValue(fromOrig + fromNet);
                        return;
                    }
                }
            }

            // 2) 女仆等非玩家实体：用调用链记录的 shooter 解析终端/女仆终端
            if (shooter != null && !(shooter instanceof Player)) {
                int fromNet = NetworkAmmoHandler.consumeFromNetwork(shooter, gunItem, stillNeed);
                if (fromNet > 0) {
                    cir.setReturnValue(fromOrig + fromNet);
                    return;
                }
            }

            // 3) handler 内携带终端的网络
            DimensionsNet terminalNet = NetworkAmmoHandler.findTerminalInHandler(itemHandler);
            if (terminalNet != null) {
                int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, terminalNet);
                if (fromNet > 0) {
                    cir.setReturnValue(fromOrig + fromNet);
                }
            }
        } finally {
            beyond$currentShooter.remove();
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
