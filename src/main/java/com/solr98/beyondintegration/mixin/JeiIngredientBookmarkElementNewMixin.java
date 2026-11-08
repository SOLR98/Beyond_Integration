package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.jei.BeyondJeiNetworkHelper;
import com.solr98.beyondintegration.jei.NetworkCountOverlay;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI 收藏栏物品扩展（新版布局：{@code IngredientGridTooltipHelper} 位于
 * {@code mezz.jei.gui.overlay.ingredients}，JEI 15.56+）。
 * <p>
 * 与旧版 {@link JeiIngredientBookmarkElementMixin} 行为一致（角标 + tooltip 数量/取物提示），
 * 二者由 {@code MixinPlugin} 按运行时布局选择应用（本类名以 NewMixin 结尾表示新布局）。
 * JEI 未安装时（@Pseudo）自动跳过。
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.elements.IngredientBookmarkElement", remap = false)
public class JeiIngredientBookmarkElementNewMixin {

    /** 数量角标：打开 BD 终端即为收藏物品创建（数量在绘制时实时查询） */
    @Inject(method = "createRenderOverlay", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void beyond$networkCountOverlay(CallbackInfoReturnable<IDrawable> cir) {
        if (cir.getReturnValue() != null) return;
        try {
            // 仅在打开 BD 终端时接管角标；未打开时保持 JEI 原行为
            if (!BeyondJeiNetworkHelper.isActive()) return;
            IElement<?> self = (IElement<?>) (Object) this;
            ItemStack stack = self.getTypedIngredient().getItemStack().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) return;
            cir.setReturnValue(new NetworkCountOverlay(stack.copyWithCount(1)));
        } catch (Throwable ignored) {}
    }

    /** tooltip：网络有库存时追加数量与取物品操作提示 */
    @Inject(method = "getTooltip", at = @At("RETURN"), remap = false, require = 0)
    private void beyond$networkTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper,
                                       IIngredientRenderer<?> ingredientRenderer, IIngredientHelper<?> ingredientHelper,
                                       CallbackInfo ci) {
        try {
            IElement<?> self = (IElement<?>) (Object) this;
            ItemStack stack = self.getTypedIngredient().getItemStack().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) return;
            long count = BeyondJeiNetworkHelper.getNetworkCount(stack);
            if (count <= 0) return;
            tooltip.add(Component.translatable("jei.beyond_integration.network_count",
                    Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jei.beyond_integration.pull_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } catch (Throwable ignored) {}
    }
}
