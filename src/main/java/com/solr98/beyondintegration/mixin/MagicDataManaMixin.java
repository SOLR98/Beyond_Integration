package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.mana.ManaAccess;
import com.solr98.beyondintegration.feature.mana.ManaNetSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Iron's Spells 网络魔力接入——瞬间回复的满蓝溢出入网。
 * <p>注入 {@link MagicData#setMana}：当本次变化为上升、且结果超过上限（如 {@code InstantManaEffect} / {@code GluttonyEffect}
 * 等瞬间回复在满蓝时），把溢出部分按 {@code sink_ratio} 沉淀入网（这部分本会被 clamp 丢弃）。
 * <p>自然回蓝的满蓝溢出由 {@code MagicManagerManaMixin} 处理；两者分别由配置
 * {@code irons_mana.sink_on_instant} / {@code sink_on_full} 控制。
 */
@Mixin(value = MagicData.class, remap = false)
public abstract class MagicDataManaMixin {

    @Shadow private ServerPlayer serverPlayer;
    @Shadow public abstract float getMana();

    @ModifyVariable(method = "setMana", at = @At("HEAD"), argsOnly = true, index = 1)
    private float beyond$sinkInstantOverflow(float mana) {
        if (this.serverPlayer == null) return mana;
        if (!CommandConfig.ironsManaEnabled()) return mana;
        if (!CommandConfig.ironsManaSinkOnInstant()) return mana;
        double ratio = CommandConfig.ironsManaSinkRatio();
        if (ratio <= 0.0) return mana;

        float old = this.getMana();
        if (mana <= old) return mana; // 仅上升（回复）路径
        float max = (float) this.serverPlayer.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        float overflow = mana - max;
        if (overflow <= 0f) return mana; // 未超上限：无溢出

        DimensionsNet net = ManaNetSource.usableNetOf(this.serverPlayer);
        if (net == null) return mana;
        long sink = (long) Math.floor(overflow * ratio);
        if (sink > 0L) {
            ManaAccess.insertMana(net, sink);
        }
        return mana; // 不改玩家值（setMana 原逻辑会 clamp）
    }
}
