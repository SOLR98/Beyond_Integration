package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.common.block.NetPathwayBlock;
import com.wintercogs.beyonddimensions.common.block.entity.NetPathwayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 网络通道（net_pathway）右键消费（1.21.1）：非 Shift 右键由方块自身整段接管，
 * 打开 BI 过滤界面并返回 {@link InteractionResult#SUCCESS}（真正消费，不再依赖事件处理器）；
 * Shift 右键不拦截，仍交给 BD 的绑定/解绑逻辑。
 */
@Mixin(value = NetPathwayBlock.class, remap = false)
public abstract class NetPathwayBlockUseMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$consumeUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult,
                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isShiftKeyDown()) return;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NetPathwayBlockEntity be) {
            player.openMenu((MenuProvider) be, buf -> buf.writeBlockPos(pos));
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
