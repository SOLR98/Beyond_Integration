package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * BD JEI 配方转移池增强（客户端，工作台/终端/通用转移共用）：
 * BD 原版 {@code TransferHelper.transferRecipe} 只把"桶物品键"加入可用池，
 * 网络只有流体（+空桶）时对应槽位被判缺失、发空键导致服务端不填。
 * <p>
 * 这里在原版池构建前追加"流体可替代"的虚拟可用量（宽松语义：网络流体量 / 每份所需，
 * 空容器可选），之后完全走 BD 原版流程——缺失高亮、maxTransfer 倍率、
 * {@code RecipeFillC2SPacket} 均保留原版行为；
 * 服务端由 {@code BdBucketCraftTransferMixin} 在提取阶段做同样的流体回退。
 */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.integration.module.jei.transfer.TransferHelper", remap = false)
public class BdBucketTransferHelperMixin {

    /** 反射缓存的私有池添加方法（@Invoker 需要接口形式，这里直接反射更简单） */
    @Unique private static Method beyond$addAvail;

    /**
     * 池局部变量赋值处注入（变量值在前、目标方法参数在后）。
     * 变量类型 Map 在方法内唯一（{@code final Map<Item, List<Avail>> pool}）。
     */
    @ModifyVariable(method = "transferRecipe", at = @At("STORE"), ordinal = 0, remap = false, require = 0)
    private static Map<Item, List<?>> beyond$addFluidAvail(
            Map<Item, List<?>> pool,
            List<Slot> inputSource, List<KeyAmount> storage, List<ItemStack> playerInv,
            IRecipeSlotsView recipeSlots, boolean maxTransfer, boolean doTransfer, boolean compressOverflow) {
        try {
            if (pool == null || storage == null || storage.isEmpty() || recipeSlots == null) return pool;
            for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
                if (slotView.getRole() != RecipeIngredientRole.INPUT) continue;
                for (ItemStack alt : slotView.getIngredients(VanillaTypes.ITEM_STACK).toList()) {
                    if (alt == null || alt.isEmpty()) continue;
                    long sub = BucketFluidHelper.countSubstitutable(storage, alt);
                    if (sub <= 0L) continue;
                    beyond$addAvail(pool, new ItemStackKey(alt), sub);
                }
            }
        } catch (Throwable ignored) {}
        return pool;
    }

    @Unique
    private static void beyond$addAvail(Map<Item, List<?>> pool, ItemStackKey key, long amount) throws Exception {
        if (beyond$addAvail == null) {
            beyond$addAvail = Class.forName("com.wintercogs.beyonddimensions.integration.module.jei.transfer.TransferHelper")
                    .getDeclaredMethod("addAvail", Map.class, ItemStackKey.class, long.class);
            beyond$addAvail.setAccessible(true);
        }
        beyond$addAvail.invoke(null, pool, key, amount);
    }
}
