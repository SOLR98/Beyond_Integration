package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.tacz.guns.api.item.IGun;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public class AbstractGunItemMixin {

    @Inject(method = "canReload", at = @At("RETURN"), cancellable = true)
    private void beyond$onCanReload(LivingEntity shooter, ItemStack gunItem, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || iGun.useInventoryAmmo(gunItem)) return;

        if (shooter instanceof FakePlayer fp) {
            if (SentryFakePlayerNetMarker.isMarked(fp)) {
                cir.setReturnValue(true);
            }
        } else if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, gunItem) > 0) {
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientCheck(gunItem, cir);
        } else {
            if (TaczAmmoExtractor.countAmmoFromMaid(shooter, gunItem) > 0) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "hasInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.useInventoryAmmo(gun)) return;

        if (shooter instanceof FakePlayer fp) {
            if (SentryFakePlayerNetMarker.isMarked(fp)) {
                cir.setReturnValue(true);
            }
        } else if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, gun) > 0) {
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientCheck(gun, cir);
        } else {
            if (TaczAmmoExtractor.countAmmoFromMaid(shooter, gun) > 0) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "findAndExtractInventoryAmmo", at = @At("RETURN"), cancellable = true)
    private void beyond$onFindAndExtract(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount, CallbackInfoReturnable<Integer> cir) {
        int found = cir.getReturnValue();
        if (found >= needAmmoCount) return;
        int stillNeed = needAmmoCount - found;
        if (stillNeed <= 0) return;

        FakePlayer fp = SentryFakePlayerNetMarker.getFakePlayerFromHandler(itemHandler);
        if (fp != null && SentryFakePlayerNetMarker.isMarked(fp)) {
            DimensionsNet net = SentryFakePlayerNetMarker.getNet(fp);
            if (net != null) {
                int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
                if (fromNet > 0) {
                    cir.setReturnValue(found + fromNet);
                    return;
                }
            }
        }

        DimensionsNet net = findTerminalInHandler(itemHandler);
        if (net != null) {
            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunItem, stillNeed, net);
            if (fromNet > 0) {
                cir.setReturnValue(found + fromNet);
            }
        }
    }

    private static DimensionsNet findTerminalInHandler(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) {
                    return net;
                }
            }
        }
        return null;
    }

    private static void clientCheck(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId != null) {
            if (!TaczAmmoCache.hasData(ammoId))
                TaczAmmoCache.requestQuick(ammoId);
            if (!TaczAmmoCache.hasData(ammoId) || TaczAmmoCache.getCount(ammoId) > 0)
                cir.setReturnValue(true);
        } else {
            cir.setReturnValue(true);
        }
    }
}
