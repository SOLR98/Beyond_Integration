package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlock", remap = false)
public class SentryArmBlockMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                       net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                       net.minecraft.world.InteractionHand hand,
                       net.minecraft.world.phys.BlockHitResult hit,
                       CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || NetedItem.getNetId(stack) < 0) return;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        if (be instanceof SentryNetIdAccessor accessor) {
            accessor.setSentryNetId(NetedItem.getNetId(stack));
        }

        try {
            Method getHeld = be.getClass().getMethod("getHeldItem");
            ItemStack held = (ItemStack) getHeld.invoke(be);
            if (held.isEmpty()) {
                if (!level.isClientSide)
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_no_gun"), true);
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            Method addBox = be.getClass().getMethod("addAmmoBox", ItemStack.class);
            boolean ok = (boolean) addBox.invoke(be, stack);
            if (ok) {
                if (!level.isClientSide && !player.isCreative()) stack.shrink(1);
            } else {
                if (!level.isClientSide)
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
        } catch (Exception ignored) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
