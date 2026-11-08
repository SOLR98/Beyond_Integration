package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.NetFurnaceWaterPurify;
import com.wintercogs.beyonddimensions.common.block.entity.BaseNetFurnaceBlockEntity;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 网络熔炉「自动组装水容器」：在 {@code workStart}（按标记槽取物）之后，
 * 把网络中水组装成带纯度的水瓶/水桶填入空闲输入槽，供 Thirst 提纯配方熔炼。
 * 仅普通网络熔炉（{@code RecipeType.SMELTING}）；高炉（BLASTING）不处理。
 */
@Mixin(value = BaseNetFurnaceBlockEntity.class, remap = false)
public abstract class NetFurnaceWaterPurifyMixin {

    @Shadow(remap = false) private RecipeType<?> recipeType;

    @Inject(method = "workStart", at = @At("TAIL"), remap = false)
    private void beyond$assembleWaterContainers(CallbackInfo ci) {
        if (recipeType != RecipeType.SMELTING) return;
        NetFurnaceWaterPurify.assemble((BaseNetFurnaceBlockEntity<?>) (Object) this);
    }
}
