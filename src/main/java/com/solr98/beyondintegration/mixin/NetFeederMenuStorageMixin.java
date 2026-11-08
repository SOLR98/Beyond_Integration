package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.init.ModFluids;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放宽网络喂食器标记槽的校验：BD 原版只接受「有食物属性」的**物品**
 * （{@code stack instanceof ItemStackKey && getFoodProperties != null}），因此
 * <ul>
 *   <li>水桶 / 模组罐（无 FoodProperties）放不进标记槽；</li>
 *   <li>**流体键**（{@code FluidStackKey}）一律拒绝。</li>
 * </ul>
 * 这里在原判定为 false 时放行两类：
 * <ol>
 *   <li><b>含水容器的物品</b>：{@code BucketItem}、药水/蜂蜜瓶、任意带
 *       {@code FLUID_HANDLER_ITEM} 能力的物品（模组罐/壶/桶）；</li>
 *   <li><b>可饮用的流体键</b>：本模组 4 档净化水，或原版水（按配置纯度）。
 *       标记后由 {@code FeederThirstHandler.tryDrinkFluid} 直接按 mB 补水。</li>
 * </ol>
 * 目标是 {@code NetFeederMenu} 里那个重写 {@code isStackValid} 的匿名
 * {@code StackHandler}（{@code NetFeederMenu$1}），MixinPlugin 会先确认该类存在。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.NetFeederMenu", remap = false)
public abstract class NetFeederMenuStorageMixin {

    @Inject(method = "isStackValid", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$allowWaterContainers(int slot, IStackKey<?> stack,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (stack == null || stack.isEmpty()) return;

        // 1) 流体键：可饮用的水（净化水 4 档 / 原版水）才能标记
        if (stack instanceof FluidStackKey fluidKey) {
            if (beyond$drinkablePurity(fluidKey.getSource()) >= 0) {
                cir.setReturnValue(true);
            }
            return;
        }

        // 2) 物品：含水容器（桶 / 瓶 / 带流体能力的容器）
        if (stack instanceof ItemStackKey itemKey && beyond$isWaterContainer(itemKey.getReadOnlyStack())) {
            cir.setReturnValue(true);
        }
    }

    /** 是否是能提供水的容器物品（桶 / 瓶 / 任意流体容器物品）。 */
    @Unique
    private static boolean beyond$isWaterContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof BucketItem) return true;
        if (item == Items.POTION || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION || item == Items.HONEY_BOTTLE) {
            return true;
        }
        // 模组容器：任何带流体容器能力的物品（罐/壶/模组桶）
        return stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM) != null;
    }

    /** 饮用水纯度：本模组 4 档水返回 0~3；原版水按配置；其它返回 -1（不可饮）。 */
    @Unique
    private static int beyond$drinkablePurity(Fluid fluid) {
        if (fluid == null) return -1;
        int purity = ModFluids.purityOf(fluid);
        if (purity < 0 && fluid == Fluids.WATER) {
            purity = CommandConfig.feederThirstVanillaWaterPurity();
        }
        return purity;
    }
}
