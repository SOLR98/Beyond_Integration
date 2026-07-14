package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = NetTerminalItem.class, remap = false)
public class NetTerminalItemMixin {

    private static final String TOKEN_KEY = "beyond$bindingToken";

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void beyond$onUse(Level level, Player player, InteractionHand usedHand,
                              CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!CommandConfig.enableTokenSystem()) return;
        if (level.isClientSide()) return;

        ItemStack stack = player.getItemInHand(usedHand);
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        UUID storedToken = beyond$readToken(stack);
        if (storedToken == null) return;

        if (!BindingTokenManager.isTokenValid(netId, storedToken)) {
            beyond$clearBinding(stack);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("msg.beyonddimensions.item_need_bound"), true);
            cir.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }

    @Inject(method = "createMenu", at = @At("HEAD"), cancellable = true)
    private void beyond$onCreateMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                     Player player, CallbackInfoReturnable<net.minecraft.world.inventory.AbstractContainerMenu> cir) {
        if (!CommandConfig.enableTokenSystem()) return;

        var ctx = NetTerminalItem.contextMap.get(player);
        if (ctx == null) return;

        ItemStack stack = ctx.stack;
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        UUID storedToken = beyond$readToken(stack);
        if (storedToken == null) return;

        if (!BindingTokenManager.isTokenValid(netId, storedToken)) {
            beyond$clearBinding(stack);
            NetTerminalItem.contextMap.remove(player);
            cir.setReturnValue(null);
        }
    }

    private static UUID beyond$readToken(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TOKEN_KEY)) {
            try { return UUID.fromString(stack.getTag().getString(TOKEN_KEY)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static void beyond$clearBinding(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove("NetId");
            stack.getTag().remove(TOKEN_KEY);
            stack.getTag().remove("beyond$bindingOwner");
        }
    }
}
