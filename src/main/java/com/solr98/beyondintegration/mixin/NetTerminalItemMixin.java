package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.terminal.TerminalBindContext;
import com.solr98.beyondintegration.feature.terminal.TerminalBindingHub;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 维度网终端 Mixin（1.20.1/Forge 版）：与 1.21.1 同步——<b>直接改</b>
 * {@code NetTerminalItem.use}：HEAD 先按视线射线找绑定目标（实体优先，其次方块），
 * 交给 {@link TerminalBindingHub} 分发；命中则消费本次使用并阻止 {@code openMenu}，
 * 未命中才放行原逻辑（打开终端界面）。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.common.item.NetTerminalItem", remap = false)
public class NetTerminalItemMixin {

    private static final double BIND_RANGE = 8.0;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = false)
    private void onUse(Level level, Player player, InteractionHand hand,
                       CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;
        ItemStack stack = player.getItemInHand(hand);
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(BIND_RANGE));

        Entity entity = findEntity(player, from, to);
        BlockPos pos = findBlock(player);
        TerminalBindContext ctx = new TerminalBindContext(player, level, stack, netId, pos, entity);

        boolean handled = false;
        if (entity != null) handled = TerminalBindingHub.dispatchEntity(ctx);
        if (!handled && pos != null) handled = TerminalBindingHub.dispatchBlock(ctx);

        if (handled) {
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(stack, level.isClientSide()));
        }
    }

    private static BlockPos findBlock(Player player) {
        HitResult hit = player.pick(BIND_RANGE, 0.0F, false);
        if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            return bhr.getBlockPos();
        }
        return null;
    }

    private static Entity findEntity(Player player, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(2);
        for (Entity e : player.level().getEntities(player, box, e -> e != player && e.isPickable())) {
            var hit = e.getBoundingBox().clip(from, to).orElse(null);
            if (hit != null) return e;
        }
        return null;
    }
}
