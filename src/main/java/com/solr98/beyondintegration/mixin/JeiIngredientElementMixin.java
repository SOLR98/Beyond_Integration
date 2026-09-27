package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.jei.BeyondJeiNetworkHelper;
import com.solr98.beyondintegration.jei.NetworkCountOverlay;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI 物品条目扩展（{@code mezz.jei.gui.overlay.elements.IngredientElement}）：
 * createRenderOverlay：打开 BD 终端时，在条目右下角叠加网络库存数量角标。
 * <p>
 * 说明：getTooltip 注入因 JEI 版本间内部类路径差异（IngredientGridTooltipHelper 的包位置随版本变动）
 * 会导致整个 Mixin 应用失败，已移除；tooltip 附加数量不再提供（Shift 取物由 JeiFocusInputMixin 负责）。
 * JEI 未安装时（@Pseudo）自动跳过；JEI 内部类结构变化时 require=0 静默失效。
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.elements.IngredientElement", remap = false)
public class JeiIngredientElementMixin {

    /** 目标类的被包装原料（ItemStack 等） */
    @Shadow(remap = false) private ITypedIngredient<?> ingredient;

    /** 数量角标：打开 BD 终端即创建（数量在绘制时实时查询，数据同步后自动显示） */
    @Inject(method = "createRenderOverlay", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void beyond$networkCountOverlay(CallbackInfoReturnable<IDrawable> cir) {
        if (cir.getReturnValue() != null) return;
        try {
            // 仅在打开 BD 终端时接管角标；未打开时保持 JEI 原行为
            if (BeyondJeiNetworkHelper.currentNetMenu() == null) return;
            ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) return;
            cir.setReturnValue(new NetworkCountOverlay(stack.copyWithCount(1)));
        } catch (Throwable ignored) {}
    }
}
