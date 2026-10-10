package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code fold_into_player}：把玩家主网络的灵魂并入 Goety 的"玩家可用魂"
 * （读取/判够/扣取），使施法、复活等也能直接调用主网络灵魂（不经过灵魂符）。
 * <p>默认关；仅 1.20.1，{@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.utils.SEHelper", remap = false)
public class SEHelperMixin {

    @Unique
    private static final ThreadLocal<Boolean> beyond$foldIn = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "getSoulAmountInt", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$foldAmount(Player player, CallbackInfoReturnable<Integer> cir) {
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulFoldIntoPlayer()) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        long total = (long) cir.getReturnValue() + SoulEnergyAccess.getSouls(net);
        cir.setReturnValue(SoulEnergyAccess.safeLongToInt(total));
    }

    @Inject(method = "getSoulsAmount", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$foldHas(Player player, int souls, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulFoldIntoPlayer()) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        if (SoulEnergyAccess.getSouls(net) >= souls) cir.setReturnValue(true);
    }

    @Inject(method = "decreaseSouls", at = @At("HEAD"), cancellable = true, remap = false)
    private static void beyond$foldDecrease(Player player, int souls, CallbackInfo ci) {
        if (beyond$foldIn.get()) return;
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulFoldIntoPlayer() || souls <= 0) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        long netSouls = SoulEnergyAccess.getSouls(net);
        if (netSouls <= 0L) return;
        long use = Math.min((long) souls, netSouls);
        long got = SoulEnergyAccess.extractSouls(net, use);
        int remaining = (int) Math.max(0L, (long) souls - got);
        beyond$foldIn.set(Boolean.TRUE);
        try {
            com.Polarice3.Goety.utils.SEHelper.decreaseSouls(player, remaining);
        } finally {
            beyond$foldIn.set(Boolean.FALSE);
        }
        ci.cancel();
    }
}
