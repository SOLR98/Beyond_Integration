package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
 * 把携带 NetId 的 BD 网络物品（便携网络终端等）放入诅咒牢笼作为灵魂符（1.21.1 useItemOn）。
 */
@Mixin(targets = "com.Polarice3.Goety.common.blocks.CursedCageBlock", remap = false)
public abstract class CursedCageBlockMixin {

    @Inject(method = "useItemOn", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$placeNetTalisman(ItemStack clickedStack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<ItemInteractionResult> cir) {
        ItemInteractionResult original = cir.getReturnValue();
        if (original != null && original != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return;
        if (level.isClientSide) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (state.getValue(com.Polarice3.Goety.common.blocks.CursedCageBlock.POWERED)) return;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof NetedItem)) return;
        if (held.getOrDefault(BDDataComponents.NET_ID_DATA, -1) < 0) return;
        if (level.getBlockEntity(pos) instanceof com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage
                && cage.getItem() != null && !cage.getItem().isEmpty()) {
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }
        ((com.Polarice3.Goety.common.blocks.CursedCageBlock) (Object) this).setItem(level, pos, state, held.copy());
        if (!player.isCreative()) held.shrink(1);
        cir.setReturnValue(ItemInteractionResult.sidedSuccess(false));
    }
}
