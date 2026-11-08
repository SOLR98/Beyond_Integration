package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.xp.XpTargetLevels;
import kotlin.ranges.IntRange;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验棒界面「目标等级」输入框范围的自定义上限。
 * <p>BD 的 {@code XpExchangeScreen.kt} 里顶层 {@code private val TARGET_RANGE = 0..MAX_TARGET_LEVEL}
 * 被 {@code BdNumberEditor} 用作输入范围（编译进 {@code XpExchangeScreenKt} 的静态字段），
 * 常量 21863 在编译期内联，只能改这个字段本身。
 * <p>这里在类初始化末尾把它换成 {@link XpTargetLevels#max()}；
 * 开关关闭时与 BD 原范围一致。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.client.ui.machine.XpExchangeScreenKt", remap = false)
public abstract class XpExchangeScreenRangeMixin {

    @Shadow
    @Final
    @Mutable
    private static IntRange TARGET_RANGE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void beyond$widenTargetRange(CallbackInfo ci) {
        TARGET_RANGE = new IntRange(0, XpTargetLevels.max());
    }
}
