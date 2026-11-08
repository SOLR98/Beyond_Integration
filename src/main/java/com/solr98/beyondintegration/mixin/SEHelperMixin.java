package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulCompat;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.solr98.beyondintegration.feature.soul.SoulVanillaSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把玩家主网络的灵魂并入 Goety 的"玩家可用魂"（读取/判够/扣取），
 * 使施法、物品、仆从维持等也能直接调用主网络灵魂（不经过灵魂符）。
 * <p>网络魂视为独立第三容器：读取叠加、判够取"合计"、扣取<b>原版优先</b>
 * （原版够则不碰网络，不足时先扣光原版再由网络补余量）。
 * <p>仅 1.20.1，{@code goety} 门控；总开关为 {@code goety_soul.enabled}。
 */
@Mixin(targets = "com.Polarice3.Goety.utils.SEHelper", remap = false)
public class SEHelperMixin {

    @Unique
    private static final ThreadLocal<Boolean> beyond$foldIn = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "getSoulAmountInt", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$foldAmount(Player player, CallbackInfoReturnable<Integer> cir) {
        if (!CommandConfig.soulEnabled()) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        long total = (long) cir.getReturnValue() + SoulEnergyAccess.getSouls(net);
        cir.setReturnValue(SoulEnergyAccess.safeLongToInt(total));
    }

    @Inject(method = "getSoulsAmount", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$foldHas(Player player, int souls, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!CommandConfig.soulEnabled()) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        // 与扣取口径一致：可用量 = 原版可用(Arca/图腾) + 网络魂，合计够即可
        // （扣取为原版优先、网络补余量；getSoulAmountInt 已并入网络量，无递归）
        if (com.Polarice3.Goety.utils.SEHelper.getSoulAmountInt(player) >= souls) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "decreaseSouls", at = @At("HEAD"), cancellable = true, remap = false)
    private static void beyond$foldDecrease(Player player, int souls, CallbackInfo ci) {
        beyond$applyDecrease(player, souls, true, ci);
    }

    /**
     * Goety ≥ 2.5.59 新增的「不打折扣魂」入口（Alca 复活/牢笼/Wight 等内部改用此方法）。
     * <p>{@code require = 0}：旧版 Goety 无此方法时静默跳过，保证向后兼容。
     */
    @Inject(method = "decreaseSoulsNoDiscount", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void beyond$foldDecreaseNoDiscount(Player player, int souls, CallbackInfo ci) {
        beyond$applyDecrease(player, souls, false, ci);
    }

    /**
     * 原版优先扣魂：原版容器足够则不碰网络；不足时先扣光原版，余量由网络补。
     *
     * @param discounted 当前入口是否会施加 {@code soulDiscount}（决定余量折算口径）
     */
    @Unique
    private static void beyond$applyDecrease(Player player, int souls, boolean discounted, CallbackInfo ci) {
        if (beyond$foldIn.get()) return;
        if (!CommandConfig.soulEnabled() || souls <= 0) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        long netSouls = SoulEnergyAccess.getSouls(net);
        if (netSouls <= 0L) return;

        // 折扣折算：原版可承担的“请求量” = 原版魂 / 折扣系数
        float d = 1.0F;
        if (discounted) {
            d = com.Polarice3.Goety.utils.SEHelper.soulDiscount(player);
            if (d <= 0.0F) return; // 100% 减免：原版即可全额承担，不碰网络
        }
        long nativeUnits = discounted
                ? (long) Math.floor(SoulVanillaSource.nativeSouls(player) / d)
                : SoulVanillaSource.nativeSouls(player);
        if (nativeUnits >= (long) souls) return; // 原版足够：完全不干预

        int nativeRequest = SoulEnergyAccess.safeLongToInt(Math.min((long) souls, nativeUnits));
        long remaining = (long) souls - nativeRequest;
        long networkTake = CommandConfig.soulNetworkDiscount()
                ? (long) Math.ceil(remaining * d)
                : remaining;
        SoulEnergyAccess.extractSouls(net, Math.min(networkTake, netSouls));

        beyond$foldIn.set(Boolean.TRUE);
        try {
            beyond$decreaseVanilla(player, nativeRequest, discounted);
        } finally {
            beyond$foldIn.set(Boolean.FALSE);
        }
        ci.cancel();
    }

    /** 只扣原版魂：折扣入口走 {@code decreaseSouls}，不折扣入口走 {@code decreaseSoulsNoDiscount}（反射，兼容旧版）。 */
    @Unique
    private static void beyond$decreaseVanilla(Player player, int amount, boolean discounted) {
        if (amount <= 0) return;
        if (discounted) {
            com.Polarice3.Goety.utils.SEHelper.decreaseSouls(player, amount);
            return;
        }
        try {
            java.lang.reflect.Method method = com.Polarice3.Goety.utils.SEHelper.class
                    .getMethod("decreaseSoulsNoDiscount", Player.class, int.class);
            method.invoke(null, player, amount);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 方法缺失/反射失败：回退到带折扣入口（仅可能出现于未知版本）
            com.Polarice3.Goety.utils.SEHelper.decreaseSouls(player, amount);
        }
    }

    @Unique
    private static final ThreadLocal<Boolean> beyond$gainIn = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 击杀沉淀的<b>旧版兜底</b>（Goety &lt; 2.5.40.0 无 {@code ChangeSoulEnergyEvent}）：
     * 在 {@code SEHelper.increaseSouls} 上把「原版容器填满后的溢出量」按 {@code kill_ratio} 沉淀到网络。
     * <p>新版（事件可用）直接返回，由 {@code SoulGainHandler}（事件）处理，避免双计。
     */
    @Inject(method = "increaseSouls", at = @At("HEAD"), cancellable = true, remap = false)
    private static void beyond$precipitateGain(Player player, int souls, CallbackInfo ci) {
        if (beyond$gainIn.get()) return;
        if (SoulCompat.gainEventAvailable()) return; // 新版由事件处理器负责
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulSourceKill() || souls <= 0) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        // 原版优先：先填满原版容器，只有溢出部分才沉淀入网
        long overflow = (long) souls - SoulVanillaSource.freeSpace(player);
        if (overflow <= 0L) return;
        long toNet = Math.round(overflow * CommandConfig.soulKillRatio());
        if (toNet <= 0L) return;
        long added = SoulEnergyAccess.insertSouls(net, toNet);
        if (added <= 0L) return;
        int remaining = (int) Math.max(0L, (long) souls - added);
        beyond$gainIn.set(Boolean.TRUE);
        try {
            com.Polarice3.Goety.utils.SEHelper.increaseSouls(player, remaining);
        } finally {
            beyond$gainIn.set(Boolean.FALSE);
        }
        ci.cancel();
    }
}
