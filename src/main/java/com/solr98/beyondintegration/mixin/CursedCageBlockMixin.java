package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把"携带 NetId 的 BD 网络物品"（便携网络终端等）放入诅咒牢笼作为<b>灵魂符</b>。
 * <p>策略：<b>先让原版 {@code use} 流程执行</b>；仅当原版未处理（返回 {@code PASS}）且
 * 手持网络物品、牢笼未通电时，于 {@code RETURN} 处<b>可取消地</b>接管（放置终端并返回成功）。
 * 这样不影响原版空手放图腾等既有交互。仅 1.20.1，{@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.common.blocks.CursedCageBlock", remap = false)
public abstract class CursedCageBlockMixin {

    @Inject(method = "use", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$placeNetTalisman(BlockState state, Level level, BlockPos pos, Player player,
                                         InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult original = cir.getReturnValue();
        // 原版已处理（成功/消费）则不动
        if (original != null && original != InteractionResult.PASS) return;
        if (level.isClientSide) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (state.getValue(com.Polarice3.Goety.common.blocks.CursedCageBlock.POWERED)) return;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return;
        if (NetedItem.getNetId(held) < 0) return;
        ((com.Polarice3.Goety.common.blocks.CursedCageBlock) (Object) this).setItem(level, pos, state, held.copy());
        cir.setReturnValue(InteractionResult.sidedSuccess(false));
    }
}
