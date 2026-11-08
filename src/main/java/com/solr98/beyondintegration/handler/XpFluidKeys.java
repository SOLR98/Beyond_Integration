package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** 网络经验流体键（BD 经验流体，1 XP = 20 mB）；与附魔/修复等经验逻辑共用。 */
public final class XpFluidKeys {

    private XpFluidKeys() {
    }

    public static FluidStackKey xpFluidKey() {
        try {
            return new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source().get(), 1));
        } catch (Throwable t) {
            return new FluidStackKey(new FluidStack(Fluids.EMPTY, 1));
        }
    }
}
