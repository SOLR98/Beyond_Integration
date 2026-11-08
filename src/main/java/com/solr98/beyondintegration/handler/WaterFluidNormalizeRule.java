package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.feature.feeder.ThirstBridge;
import com.solr98.beyondintegration.init.ModFluids;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 入网流体纯度规范化：把「原版水 + Purity NBT」统一转换成本模组的 4 档水
 * （{@code beyond_integration:{dirty,slightly_dirty,acceptable,purified}_water}）。
 * <p>
 * 由 Thirst 的沙滤/瓶装水等路径以流体形式直接插入网络时，会带 {@code Purity} NBT
 * 存成 {@code minecraft:water}；与 {@link BucketSeparatorHandler} 拆桶得到的 BI 档流体
 * 形成两套表示，导致终端取水时无法还原原先的纯度。本规则在入网阶段把带纯度的原版水
 * 按 1:1 替换为对应档位的 BI 流体，保证网络内只有一种表示。
 * <p>
 * 不带纯度标签的原版水保持原样（不改变与非口渴模组的兼容行为）。
 */
public final class WaterFluidNormalizeRule implements InsertRule {

    public static final ResourceLocation ID =
            new ResourceLocation("beyond_integration", "water_purity_normalize");

    /** 注册为插入规则（与桶分离规则一起在 commonSetup 注册）。 */
    public static void register() {
        UnifiedStorageRules.insertMatching(ID, WaterFluidNormalizeRule::matches, new WaterFluidNormalizeRule());
    }

    private static boolean matches(IStackKey<?> key) {
        if (!(key instanceof FluidStackKey fk)) return false;
        if (fk.getSource() != Fluids.WATER) return false;
        return purityOf(fk) >= 0;
    }

    private static int purityOf(FluidStackKey fk) {
        FluidStack stack = fk.getReadOnlyStack();
        return ThirstBridge.get().readFluidPurity(stack);
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request) {
        if (!(request.key() instanceof FluidStackKey fk)) return null;
        if (fk.getSource() != Fluids.WATER) return null;

        int purity = purityOf(fk);
        if (purity < 0 || purity > 3) return null;

        Fluid tier = ModFluids.tierFluid(purity);
        if (tier == null) return null;

        // 1:1 替换：每 1 mB 原版水换成 1 mB 对应档位的 BI 水
        return InsertPlan.convert(1).add(new FluidStackKey(new FluidStack(tier, 1)), 1);
    }
}
