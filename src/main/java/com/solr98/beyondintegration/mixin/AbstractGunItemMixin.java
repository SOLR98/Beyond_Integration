package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.solr98.beyondintegration.compat.tacz.TaczAmmoShooterContext;
import com.solr98.beyondintegration.compat.tacz.TaczDummyAmmoCompat;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.feature.ammo.tacz.NetworkAmmoHandler;
import com.solr98.beyondintegration.feature.ammo.tacz.NetworkAwareAmmoHandler;
import com.tacz.guns.api.item.IGun;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入目标：TacZ 的 {@code AbstractGunItem}（枪械物品基类）。
 * 目的：扩展枪械"能否装填/是否有库存弹药/弹药提取"的判断逻辑，使 FakePlayer、
 * 网络终端（Beyond Dimensions）以及东方女仆也能使用网络中的弹药。
 */
@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public class AbstractGunItemMixin {

    private static final Logger LOGGER = LogUtils.getLogger();



    // 拦截 canReload：原逻辑判定不可装填时，再根据射击者类型（FakePlayer/服务端玩家/客户端/女仆）检查网络弹药
    @Inject(method = "canReload", at = @At("RETURN"), cancellable = true)
    private void beyond$onCanReload(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        TaczAmmoShooterContext.set(shooter);
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || iGun.useInventoryAmmo(gunItem)) return;

        if (shooter instanceof FakePlayer fp) {
            if (SentryFakePlayerNetMarker.isMarked(fp)) {
                LOGGER.debug("canReload: FakePlayer has network marker");
                cir.setReturnValue(true);
            }
        } else if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, gunItem) > 0) {
                LOGGER.debug("canReload: player {} has network ammo", sp.getName().getString());
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientCheck(gunItem, cir);
        } else {
            if (TaczAmmoExtractor.countAmmoFromMaid(shooter, gunItem) > 0) {
                LOGGER.debug("canReload: maid has network ammo");
                cir.setReturnValue(true);
            }
        }
    }

    // 拦截 hasInventoryAmmo：与 canReload 相同思路，判定库存弹药时把网络弹药计入；
    // 1.2.0 的虚拟备弹（useDummyAmmo）枪同样走网络兜底。
    @Inject(method = "hasInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo, CallbackInfoReturnable<Boolean> cir) {
        TaczAmmoShooterContext.set(shooter);
        LOGGER.info("[BD-Ammo] hasInventoryAmmo enter side={} shooter={} ret={} needCheck={}",
                shooter != null && shooter.level().isClientSide(),
                shooter == null ? "null" : shooter.getName().getString(),
                cir.getReturnValue(), needCheckAmmo);
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || (!iGun.useInventoryAmmo(gun) && !TaczDummyAmmoCompat.useDummyAmmo(iGun, gun))) return;

        if (shooter instanceof FakePlayer fp) {
            if (SentryFakePlayerNetMarker.isMarked(fp)) {
                LOGGER.debug("hasInventoryAmmo: FakePlayer has network marker");
                cir.setReturnValue(true);
            }
        } else if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, gun) > 0) {
                LOGGER.debug("hasInventoryAmmo: player {} has network ammo", sp.getName().getString());
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientCheck(gun, cir);
        } else {
            if (TaczAmmoExtractor.countAmmoFromMaid(shooter, gun) > 0) {
                LOGGER.debug("hasInventoryAmmo: maid has network ammo");
                cir.setReturnValue(true);
            }
        }
    }

    // 拦截 findAndExtractInventoryAmmo：原逻辑从物品栏提取弹药不足时，补充从网络（FakePlayer 绑定网或物品栏内终端网）直接消耗
    @Inject(method = "findAndExtractInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onFindAndExtract(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount, CallbackInfoReturnable<Integer> cir) {
        int found = cir.getReturnValue();
        if (found >= needAmmoCount) return;
        int stillNeed = needAmmoCount - found;
        if (stillNeed <= 0) return;
        LOGGER.info("[BD-Ammo] findAndExtract found={} need={} still={} recorded={}",
                found, needAmmoCount, stillNeed,
                TaczAmmoShooterContext.get() == null ? "null" : TaczAmmoShooterContext.get().getName().getString());

        FakePlayer fp = SentryFakePlayerNetMarker.getFakePlayerFromHandler(itemHandler);
        if (fp != null && SentryFakePlayerNetMarker.isMarked(fp)) {
            DimensionsNet net = SentryFakePlayerNetMarker.getNet(fp);
            if (net != null) {
                int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
                if (fromNet > 0) {
                    LOGGER.debug("findAndExtract: FakePlayer consumed {} from net#{} (need={})", fromNet, net.getId(), stillNeed);
                    cir.setReturnValue(found + fromNet);
                    return;
                }
            }
        }

        // 1) 调用链记录的射击者（LivingEntityShootMixin 在 consumeAmmoFromPlayer 入口写入）：
        //    背包直读（FeedType.INVENTORY）射击每发走这里，从玩家主网络/女仆终端网络补扣。
        LivingEntity recordedShooter = TaczAmmoShooterContext.get();
        if (recordedShooter instanceof ServerPlayer sp) {
            int fromNet = NetworkAwareAmmoHandler.consumeFromNetworks(sp, gunItem, stillNeed);
            if (fromNet > 0) {
                LOGGER.debug("findAndExtract: player {} consumed {} from primary net (need={})",
                        sp.getName().getString(), fromNet, stillNeed);
                cir.setReturnValue(found + fromNet);
                TaczAmmoShooterContext.clear();
                return;
            }
        } else if (recordedShooter != null && !(recordedShooter instanceof net.minecraft.world.entity.player.Player)) {
            int fromNet = NetworkAmmoHandler.consumeFromNetwork(recordedShooter, gunItem, stillNeed);
            if (fromNet > 0) {
                cir.setReturnValue(found + fromNet);
                TaczAmmoShooterContext.clear();
                return;
            }
        }

        // 2) 兜底：handler 是玩家主背包（未经过 consumeAmmoFromPlayer 的调用链）
        if (itemHandler instanceof net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper wrapper) {
            var owner = wrapper.getInventoryPlayer().player;
            if (owner instanceof ServerPlayer sp) {
                int fromNet = NetworkAwareAmmoHandler.consumeFromNetworks(sp, gunItem, stillNeed);
                if (fromNet > 0) {
                    cir.setReturnValue(found + fromNet);
                    TaczAmmoShooterContext.clear();
                    return;
                }
            }
        }

        DimensionsNet net = findTerminalInHandler(itemHandler);
        if (net != null) {
            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
            if (fromNet > 0) {
                LOGGER.debug("findAndExtract: consumed {} from net#{} (need={}, found={})",
                        fromNet, net.getId(), stillNeed, found);
                cir.setReturnValue(found + fromNet);
            } else {
                LOGGER.debug("findAndExtract: net#{} has ammo but consume returned 0", net.getId());
            }
        }
        TaczAmmoShooterContext.clear();
    }

    // 在物品栏容器中查找携带网络 ID 数据组件的终端物品（如维度网络终端），返回其对应的网络
    private static DimensionsNet findTerminalInHandler(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) {
                    LOGGER.debug("findTerminalInHandler: found net#{} terminal in slot {}", netId, i);
                    return net;
                }
            }
        }
        return null;
    }

    // 客户端兜底检查：客户端只负责表现，弹药是否可用由服务端权威判断/扣减。
    // TACZ 1.2.0 的 LocalPlayerShoot / GunAnimationStateContext 会依据 hasInventoryAmmo/
    // hasAmmoToConsume 提前返回（背包直读枪无弹药就不发开火包），而本地快照可能滞后或过期
    // （镜像未开、推送未到、网络弹药为 0 等），因此这里一律乐观放行并顺带请求快照。
    private static void clientCheck(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId != null) {
            TaczAmmoCache.requestQuick(ammoId);
        }
        LOGGER.info("[BD-Ammo] clientCheck ammoId={} hasData={} count={}",
                ammoId == null ? "null" : ammoId,
                ammoId != null && TaczAmmoCache.hasData(ammoId),
                ammoId != null ? TaczAmmoCache.getCount(ammoId) : -1);
        cir.setReturnValue(true);
    }


}
