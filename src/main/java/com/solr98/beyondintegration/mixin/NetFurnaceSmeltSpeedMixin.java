package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 网络熔炉「自定义熔炼速度」（BD 修改，配置 {@code bd_tweaks.net_furnace_smelt_speed}）。
 * 把 {@code workContent} 读取的配方耗时按倍率缩放（至少 1 tick），从而加快/放慢网络熔炉的物品熔炼。
 */
@Mixin(value = BaseNetFurnaceBlockEntity.class, remap = false)
public abstract class NetFurnaceSmeltSpeedMixin {

    @Redirect(method = "workContent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;m_43753_()I"),
            remap = false, require = 0)
    private int beyond$scaledCookTime(AbstractCookingRecipe recipe) {
        int base = recipe.getCookingTime();
        double speed = CommandConfig.netFurnaceSmeltSpeed();
        if (speed <= 0.0D) return base;
        return Math.max(1, (int) Math.round(base / speed));
    }
}
