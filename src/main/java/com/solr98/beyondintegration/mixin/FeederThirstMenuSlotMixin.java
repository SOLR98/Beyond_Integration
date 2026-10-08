package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.ThirstBridge;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放宽喂食器标记槽位校验：BD 原本只允许「有食物属性」的物品，
 * 这里额外允许「能补水」的纯饮料（水瓶/药水/茶等，无食物属性）放入标记槽。
 * <p>目标是 {@code NetFeederMenu} 内联的匿名 {@code StackHandler}（{@code NetFeederMenu$1}）。
 * 仅在 {@code thirst} 已加载时由 {@code MixinPlugin} 应用。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.NetFeederMenu$1", remap = false)
public class FeederThirstMenuSlotMixin
{
    @Inject(method = "isStackValid", at = @At("RETURN"), cancellable = true)
    private void beyondintegration$allowThirstItems(int slot, IStackKey<?> stack, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ()) return;
        // 补水食物
        if (stack instanceof ItemStackKey itemStackKey
                && ThirstBridge.get().restoresThirst(itemStackKey.getReadOnlyStack()))
        {
            cir.setReturnValue(true);
            return;
        }
        // 流体标记：网络喂食器允许把水等流体键标记到槽位（否则读取/保存时会被 isStackValid 丢弃）
        if (stack instanceof FluidStackKey)
        {
            cir.setReturnValue(true);
        }
    }
}
