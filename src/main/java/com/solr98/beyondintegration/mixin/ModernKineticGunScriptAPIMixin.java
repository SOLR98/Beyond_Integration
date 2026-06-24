package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class ModernKineticGunScriptAPIMixin {
    @Shadow(remap = false) private LivingEntity shooter;
    @Shadow(remap = false) private ItemStack itemStack;

    @Inject(method = "consumeAmmoFromPlayer", at = @At("RETURN"), cancellable = true)
    private void beyond$onConsumeAmmoFromPlayer(int neededAmount, CallbackInfoReturnable<Integer> cir) {
        int found = cir.getReturnValue();
        if (found >= neededAmount) return;
        int stillNeed = neededAmount - found;

        if (shooter instanceof FakePlayer fp) {
            DimensionsNet net = SentryFakePlayerNetMarker.getNet(fp);
            if (net != null) {
                int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(itemStack, stillNeed, net);
                if (fromNet > 0) {
                    cir.setReturnValue(found + fromNet);
                }
            }
        } else if (shooter instanceof ServerPlayer sp) {
            int fromNet = TaczAmmoExtractor.tryConsumeFromAll(sp, itemStack, stillNeed);
            if (fromNet <= 0) {
                fromNet = consumeFromInventoryTerminal(sp, itemStack, stillNeed);
            }
            if (fromNet > 0) {
                cir.setReturnValue(found + fromNet);
            }
        } else {
            int fromNet = TaczAmmoExtractor.tryConsumeFromMaid(shooter, itemStack, stillNeed);
            if (fromNet > 0) {
                cir.setReturnValue(found + fromNet);
            }
        }
    }

    @Inject(method = "hasAmmoToConsume", at = @At("RETURN"), cancellable = true)
    private void beyond$onHasAmmoToConsume(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        if (shooter instanceof FakePlayer fp) {
            if (SentryFakePlayerNetMarker.isMarked(fp)) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (shooter instanceof ServerPlayer sp) {
            if (TaczAmmoExtractor.countAmmoFromAll(sp, itemStack) > 0) {
                cir.setReturnValue(true);
            }
        } else if (shooter.level().isClientSide()) {
            clientAmmoCheck(itemStack, cir);
        } else if (TaczAmmoExtractor.countAmmoFromMaid(shooter, itemStack) > 0) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static int consumeFromInventoryTerminal(ServerPlayer player, ItemStack gunStack, int need) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) {
                    int taken = TaczAmmoExtractor.consumeAmmoDirectly(gunStack, need, net);
                    if (taken > 0) {
                        return taken;
                    }
                }
            }
        }
        return 0;
    }

    @Unique
    private static void clientAmmoCheck(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
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
