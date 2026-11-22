package com.solr98.beyondintegration.mixin.tacz;

import com.solr98.beyondintegration.modules.tacz.TarCaliberSupport;
import com.solr98.beyondintegration.modules.tacz.TaczAmmoExtractor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * TacZ Caliber Ammo 兼容：把 BD 维度网络接进 TAR 的换弹序列。
 *
 * <p><b>当前状态：临时禁用。</b>由 {@code MixinPlugin} 对 {@code com.tacz_caliber_ammo.*} 目标直接返回 false，
 * 本类不会被应用（功能不可用，但不影响正常运行）。待 TACZ 1.1.8 生产验证通过后，把该判断恢复为
 * “TAR 已加载 && 目标类存在”即可重新启用。
 *
 * <p>两个注入点都在 TAR 的 {@code com.tacz_caliber_ammo.reload.PouchReloadSource} 上，
 * 目标类用字符串指定，TAR 未安装时由 MixinPlugin 直接跳过；本类不 import 任何 TAR 类型。
 *
 * <ul>
 *   <li>{@code hasUsableAmmo}：原本只认快捷栏里的弹药包。网络里有该枪口径的弹药时也返回 true，
 *       否则 {@code canReload} / {@code hasInventoryAmmo} 会先把换弹挡住。</li>
 *   <li>{@code supply}：原本按弹药包图案/背包供弹，弹药不足就停。返回值（逐发序列）总发数不足
 *       {@code need} 时，从 BD 网络按「枪自身 ammoId → 同口径其它变体」的顺序补足，
 *       并把补到的部分追加为 {@code Round}，保持 TAR 的逐发语义。</li>
 * </ul>
 */
// 临时禁用（2026-11-22）：TAR 0.5.1 只支持 TACZ 1.1.8，生产验证未完成前不注入。
// 恢复步骤：① 取消下面 @Mixin 的注释；② 在 beyond_integration.mixins.json 的 mixins 数组里加回 "tacz.TarReloadMixin"；
//          ③ build.gradle 里给 mods/ 的 TAR jar 加回 compileOnly（当前 mods 只排除运行期）。
// @Mixin(targets = "com.tacz_caliber_ammo.reload.PouchReloadSource", remap = false)
public class TarReloadMixin {

    /** 网络有该枪口径的弹药时，允许进入换弹流程。 */
    // @Inject(method = "hasUsableAmmo", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void beyond$hasUsableAmmo(Player player, ItemStack gun,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || !(player instanceof ServerPlayer sp) || gun == null || gun.isEmpty()) {
            return;
        }
        if (!TarCaliberSupport.loaded()) {
            return;
        }
        if (TarCaliberSupport.countAvailable(gun, sp) > 0L) {
            cir.setReturnValue(true);
        }
    }

    /** 弹药包/背包供弹不足时，用 BD 网络补足逐发序列。 */
    // @Inject(method = "supply", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void beyond$supply(Player player, ItemStack pouch, int startPos, int need,
                                      ItemStack gun, CallbackInfoReturnable<List<?>> cir) {
        if (!(player instanceof ServerPlayer sp) || gun == null || gun.isEmpty() || need <= 0) {
            return;
        }
        if (!TarCaliberSupport.loaded()) {
            return;
        }
        List<?> origin = cir.getReturnValue();
        List<Object> sequence = origin == null ? new ArrayList<>() : new ArrayList<>(origin);
        int supplied = 0;
        for (Object round : sequence) {
            supplied += TarCaliberSupport.roundCount(round);
        }
        int still = need - supplied;
        if (still <= 0) {
            return;
        }

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
        if (net == null) {
            return;
        }

        int fromNet = 0;
        for (ResourceLocation ammoId : TarCaliberSupport.candidateAmmoIds(gun)) {
            if (still <= 0) {
                break;
            }
            int consumed = TaczAmmoExtractor.consumeAmmoByAmmoId(ammoId, still, net);
            if (consumed <= 0) {
                continue;
            }
            Object round = TarCaliberSupport.newRound(ammoId, consumed);
            if (round == null) {
                // 构造失败：把已扣的弹药退回网络，避免凭空消失
                TaczAmmoExtractor.refundAmmoByAmmoId(ammoId, consumed, net);
                continue;
            }
            sequence.add(round);
            still -= consumed;
            fromNet += consumed;
        }
        if (fromNet > 0) {
            cir.setReturnValue(sequence);
        }
    }
}
