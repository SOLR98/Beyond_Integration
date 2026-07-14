package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.item.NetedItem", remap = false)
public class NetedItemMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TOKEN_KEY = "beyond$bindingToken";
    private static final String OWNER_KEY = "beyond$bindingOwner";
    private static final ThreadLocal<Integer> beyond$pendingUnbindNetId = new ThreadLocal<>();

    @Inject(method = "getNet", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$onGetNet(ItemStack stack, CallbackInfoReturnable<DimensionsNet> cir) {
        if (!CommandConfig.enableTokenSystem()) return;

        DimensionsNet net = cir.getReturnValue();
        if (net == null) return;
        if (BindingTokenManager.getInstance() == null) return;

        int netId = NetedItem.getNetId(stack);
        UUID storedToken = beyond$readToken(stack);
        UUID owner = beyond$readOwner(stack);

        if (storedToken == null) {
            UUID token = BindingTokenManager.getOrCreateToken(netId, owner != null ? owner : UUID.randomUUID());
            beyond$writeToken(stack, token);
            return;
        }

        if (!BindingTokenManager.isTokenValid(netId, storedToken)) {
            cir.setReturnValue(null);
            beyond$clearBinding(stack);
        }
    }

    @Inject(method = "setNet", at = @At("HEAD"), remap = false)
    private static void beyond$onSetNet(ItemStack itemstack, Player player, CallbackInfoReturnable<Boolean> cir) {
        int netId = NetedItem.getNetId(itemstack);
        if (netId >= 0) {
            beyond$pendingUnbindNetId.set(netId);
            beyond$writeToken(itemstack, null);
        } else {
            beyond$pendingUnbindNetId.remove();
        }
    }

    @Inject(method = "setNet", at = @At("RETURN"), remap = false)
    private static void beyond$onSetNetPost(ItemStack itemstack, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (BindingTokenManager.getInstance() == null) return;

        int netId = NetedItem.getNetId(itemstack);
        if (netId >= 0) {
            UUID owner = player.getUUID();
            UUID token = BindingTokenManager.getOrCreateToken(netId, owner);
            beyond$writeToken(itemstack, token);
            beyond$writeOwner(itemstack, owner);
            String itemName = itemstack.getDisplayName().getString();
            if (CommandConfig.enableAuditLog()) {
                NetworkBindingRegistry.recordItemBind(netId, itemName, player.getName().getString(), owner);
            }
        } else {
            Integer prevNetId = beyond$pendingUnbindNetId.get();
            if (prevNetId != null) {
                String itemName = itemstack.getDisplayName().getString();
                if (CommandConfig.enableAuditLog()) {
                    String displayName = itemstack.getDisplayName().getString();
                NetworkBindingRegistry.removeItemBind(prevNetId, displayName);
                }
                beyond$pendingUnbindNetId.remove();
            }
        }
    }

    @Unique
    private static UUID beyond$readToken(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TOKEN_KEY)) {
            try { return UUID.fromString(stack.getTag().getString(TOKEN_KEY)); } catch (Exception ignored) {}
        }
        return null;
    }

    @Unique
    private static void beyond$writeToken(ItemStack stack, UUID token) {
        if (token != null) {
            stack.getOrCreateTag().putString(TOKEN_KEY, token.toString());
        } else if (stack.hasTag()) {
            stack.getTag().remove(TOKEN_KEY);
        }
    }

    @Unique
    private static UUID beyond$readOwner(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(OWNER_KEY)) {
            try { return UUID.fromString(stack.getTag().getString(OWNER_KEY)); } catch (Exception ignored) {}
        }
        return null;
    }

    @Unique
    private static void beyond$writeOwner(ItemStack stack, UUID uuid) {
        if (uuid != null) {
            stack.getOrCreateTag().putString(OWNER_KEY, uuid.toString());
        } else if (stack.hasTag()) {
            stack.getTag().remove(OWNER_KEY);
        }
    }

    @Unique
    public static void beyond$clearBinding(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove("NetId");
            stack.getTag().remove(TOKEN_KEY);
            stack.getTag().remove(OWNER_KEY);
        }
    }
}
