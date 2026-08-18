package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入超级战争的 VehicleEntity（仅客户端）：
 * 修复手持带 NetId 的维度网络物品右键载具时被原版交互（如打开载具界面）抢占的问题——
 * 直接返回 SUCCESS 拦截交互，保留给服务端网络逻辑处理。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = VehicleEntity.class, remap = false)
public class VehicleClientInteractMixin {

    /** 手持网络物品时吞掉客户端交互结果 */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void beyond$onInteract(Player player, InteractionHand hand,
                                   CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;
        if (NetedItem.getNetId(stack) < 0) return;

        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
