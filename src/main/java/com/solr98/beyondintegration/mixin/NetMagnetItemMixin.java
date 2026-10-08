package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.magnet.MagnetHandler;
import com.wintercogs.beyonddimensions.common.item.NetMagnetItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 网络磁铁（1.21.1）：整段替换 BD {@code NetMagnetItem.workContent} / {@code getTicksPerWork}，由 BI 接管，
 * 使“吸取范围 / 间隔”使用 BI 自定义档位列表（见 {@link MagnetHandler}）。
 */
@Mixin(value = NetMagnetItem.class, remap = false)
public class NetMagnetItemMixin {

    /**
     * 不改 @Overwrite：保留原 workContent 方法体，避免与 rs_integration 等对 workContent 内调用点的
     * @WrapOperation 注入冲突（@Overwrite 会移除注入点导致对方 mixin 应用失败崩溃）。
     * BI 在 HEAD 接管并取消原逻辑。
     */
    @Inject(method = "workContent", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$workContent(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected, CallbackInfo ci) {
        if (!level.isClientSide()) {
            MagnetHandler.handle(stack, level, holder, slotId, isSelected);
        }
        ci.cancel();
    }

    @Overwrite(remap = false)
    public int getTicksPerWork(ItemStack stack, Level level, Entity holder, int slotId, boolean isSelected) {
        return MagnetHandler.ticksPerWork(stack);
    }
}
