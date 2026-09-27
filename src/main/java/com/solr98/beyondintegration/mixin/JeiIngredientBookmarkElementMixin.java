package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.jei.BeyondJeiNetworkHelper;
import com.solr98.beyondintegration.jei.NetworkCountOverlay;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI 收藏栏物品扩展（{@code mezz.jei.gui.overlay.elements.IngredientBookmarkElement}）：
 * createRenderOverlay：打开 BD 终端时，为左侧收藏物品叠加网络库存数量角标。
 * <p>
 * 说明：与物品列表条目一致，不注入 getTooltip（避免 JEI 版本间内部类路径差异导致 Mixin 应用失败）。
 * JEI 未安装时（@Pseudo）自动跳过；JEI 内部类结构变化时 require=0 静默失效。
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.elements.IngredientBookmarkElement", remap = false)
public class JeiIngredientBookmarkElementMixin {

    /** 数量角标：打开 BD 终端即为收藏物品创建（数量在绘制时实时查询） */
    @Inject(method = "createRenderOverlay", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void beyond$networkCountOverlay(CallbackInfoReturnable<IDrawable> cir) {
        if (cir.getReturnValue() != null) return;
        try {
            // 仅在打开 BD 终端时接管角标；未打开时保持 JEI 原行为
            if (BeyondJeiNetworkHelper.currentNetMenu() == null) return;
            IElement<?> self = (IElement<?>) (Object) this;
            ItemStack stack = self.getTypedIngredient().getItemStack().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) return;
            cir.setReturnValue(new NetworkCountOverlay(stack.copyWithCount(1)));
        } catch (Throwable ignored) {}
    }
}
