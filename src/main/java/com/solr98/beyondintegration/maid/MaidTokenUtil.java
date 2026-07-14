package com.solr98.beyondintegration.maid;

import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MaidTokenUtil {

    private static final String TOKEN_KEY = "beyond$bindingToken";
    private static final String OWNER_KEY = "beyond$bindingOwner";

    public static UUID readToken(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TOKEN_KEY)) {
            try { return UUID.fromString(stack.getTag().getString(TOKEN_KEY)); } catch (Exception ignored) {}
        }
        return null;
    }

    public static void writeToken(ItemStack stack, UUID token) {
        stack.getOrCreateTag().putString(TOKEN_KEY, token.toString());
    }

    public static UUID readOwner(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(OWNER_KEY)) {
            try { return UUID.fromString(stack.getTag().getString(OWNER_KEY)); } catch (Exception ignored) {}
        }
        return null;
    }

    public static void clearBinding(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove("NetId");
            stack.getTag().remove(TOKEN_KEY);
            stack.getTag().remove(OWNER_KEY);
        }
    }

    public static boolean isTokenInvalid(ItemStack stack, int netId) {
        UUID token = readToken(stack);
        return token != null && !BindingTokenManager.isTokenValid(netId, token);
    }
}
