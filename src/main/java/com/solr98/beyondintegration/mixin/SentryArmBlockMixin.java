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

/**
 * 注入哨戒机械臂模组(SentryMechanicalArm)的 SentryArmBlock：
 * 扩展右键交互——手持带 NetId 的维度网络物品（NetedItem）点击哨戒底座时，
 * 将物品存入哨戒弹药箱并绑定对应网络（写入 SentryNetId），支持玩家背包补充弹药。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlock", remap = false)
public class SentryArmBlockMixin {

    /** 拦截方块右键：处理网络物品绑定/装填逻辑，成功后取消原版交互 */
    @Inject(method = {"m_6227_", "use"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void onUse(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                       net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                       net.minecraft.world.InteractionHand hand,
                       net.minecraft.world.phys.BlockHitResult hit,
                       CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int netId = NetedItem.getNetId(stack);
        if (stack.isEmpty() || netId < 0) return;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

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
                if (be instanceof SentryNetIdAccessor accessor) {
                    accessor.setSentryNetId(netId);
                }
                if (!level.isClientSide && !player.isCreative()) stack.shrink(1);
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_net_bound", netId),
                            true);
                }
            } else {
                if (!level.isClientSide)
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
        } catch (Exception ignored) {}

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
