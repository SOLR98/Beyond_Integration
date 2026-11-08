package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.mana.ManaAccess;
import com.solr98.beyondintegration.feature.mana.ManaNetSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iron's Spells 网络魔力接入——自然回蓝向网络回蓝。
 * <p>注入 {@link MagicManager#regenPlayerMana}：
 * <ul>
 *   <li><b>溢出模式</b>（默认）：玩家满蓝（原返回 {@code false}、本次回蓝被浪费）且 {@code sink_on_full} 时，
 *       把本次增量按 {@code sink_ratio} 沉淀入网；</li>
 *   <li><b>固定模式</b>（{@code fixed_regen}）：每次自然回蓝固定向网络注入 {@code fixed_regen_amount}；
 *       玩家未满时是否也注入由 {@code regen_when_not_full} 决定。</li>
 * </ul>
 * 网络独立回蓝（{@code independent_regen}）不在此处理，见 {@code ManaNetRegenHandler}。
 */
@Mixin(value = MagicManager.class, remap = false)
public abstract class MagicManagerManaMixin {

    @Inject(method = "regenPlayerMana", at = @At("RETURN"))
    private void beyond$regenSink(ServerPlayer serverPlayer, MagicData playerMagicData,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!CommandConfig.ironsManaEnabled()) return;
        if (CommandConfig.ironsManaIndependentRegen()) return; // 独立回蓝由 ManaNetRegenHandler 处理
        boolean full = !cir.getReturnValueZ(); // 满蓝时原方法返回 false

        DimensionsNet net = ManaNetSource.usableNetOf(serverPlayer);
        if (net == null) return;

        if (CommandConfig.ironsManaFixedRegen()) {
            if (!full && !CommandConfig.ironsManaRegenWhenNotFull()) return;
            long amount = CommandConfig.ironsManaFixedRegenAmount();
            if (amount > 0L) {
                ManaAccess.insertMana(net, amount);
            }
            return;
        }

        if (!full) return;
        if (!CommandConfig.ironsManaSinkOnFull()) return;
        double ratio = CommandConfig.ironsManaSinkRatio();
        if (ratio <= 0.0) return;
        int maxMana = (int) serverPlayer.getAttributeValue(AttributeRegistry.MAX_MANA);
        if (maxMana <= 0) return;
        float regenPct = (float) serverPlayer.getAttributeValue(AttributeRegistry.MANA_REGEN);
        float increment = maxMana * regenPct * 0.01f
                * (float) com.solr98.beyondintegration.feature.mana.IronsConfigCompat.manaRegenMultiplier();
        if (increment <= 0f) return;
        long sink = (long) Math.floor(increment * ratio);
        if (sink > 0L) {
            ManaAccess.insertMana(net, sink);
        }
    }
}
