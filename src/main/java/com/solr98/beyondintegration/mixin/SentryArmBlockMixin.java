package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlock", remap = false)
public class SentryArmBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void beyond$onUseItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                     Player player, InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<ItemInteractionResult> cir) {
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SentryArmBlockEntity sentry)) return;

        ItemStack held = sentry.getHeldItem();
        if (held.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_no_gun"), true);
            }
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }

        boolean ok = sentry.addAmmoBox(stack);
        if (ok) {
            if (!level.isClientSide && !player.isCreative()) stack.shrink(1);
        } else {
            if (!level.isClientSide)
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
        }
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}
