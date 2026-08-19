package com.solr98.beyondintegration.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 与 taczaddon 的配方原料计数显示兼容（仅 1.1.8.1，MixinPlugin 门控；模式互斥）：
 *
 * - 非网络模式（addon 模式）：计数 = 背包 + 外部容器，addon 原样处理；
 * - 网络模式：计数 = 背包 + 网络（不含 addon 外部容器）。
 *
 * taczaddon 的 GunSmithTableSourceViewMixin 以 HEAD cancellable 注入
 * getPlayerIngredientCount（有外部源时替换为 背包+外部 并 cancel 原方法）。
 * 本 Mixin 在外部源数据层（GunSmithExternalSourceState.getExternalDisplayStacks）
 * 拦截：网络模式下返回空列表，使 addon 的计数替换条件恒为"无外部源"→
 * 不 cancel 原方法 → TACZ 原逻辑（背包）执行，本模组的
 * getPlayerIngredientCount RETURN 叠加网络数量生效。
 * 顺序无关：无论 Mixin 应用顺序，addon 的替换行为都依赖该数据源。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.client.GunSmithExternalSourceState", remap = false)
public abstract class TaczaddonIngredientCountMixin {

    /** 网络模式下隐藏 addon 外部容器来源（模式互斥：网络模式只用背包+网络） */
    @Inject(method = "getExternalDisplayStacks", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$hideExternalSourcesInNetMode(CallbackInfoReturnable<List<ItemStack>> cir) {
        if (com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()) {
            cir.setReturnValue(List.of());
        }
    }
}
