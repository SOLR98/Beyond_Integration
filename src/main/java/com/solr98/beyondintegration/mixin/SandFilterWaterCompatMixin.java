package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.init.ModFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 口渴（Thirst）Create 兼容——沙滤（Sand Filter）对 BI 纯度水的支持（1.21.1）。
 * <p>Thirst 的沙滤只对原版水 + Thirst 纯度组件生效；BI 的 4 档水是独立流体，会被原样透传。
 * 这里在沙滤 {@code tick} 中把抽出的 BI 档水替换为“高一档”的 BI 水（到顶保持纯净），实现过滤提纯。
 * <p>Thirst 未安装时不加载（{@code @Pseudo}）。
 */
@Pseudo
@Mixin(targets = "cn.mlus.thirst.compat.create.SandFilterBlockEntity", remap = false)
public abstract class SandFilterWaterCompatMixin {

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0, remap = false, require = 0)
    private FluidStack bi$purifyBiWater(FluidStack water) {
        if (water == null || water.isEmpty()) return water;
        int purity = ModFluids.purityOf(water.getFluid());
        if (purity >= 0 && purity < 3) {
            Fluid next = ModFluids.tierFluid(purity + 1);
            if (next != null) return new FluidStack(next, water.getAmount());
        }
        return water;
    }
}
