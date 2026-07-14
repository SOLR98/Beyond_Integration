package com.solr98.beyondintegration.core.util;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ItemTokenUtil {

    private static final String TOKEN_KEY = "beyond$bindingToken";
    private static final String OWNER_KEY = "beyond$bindingOwner";

    public static UUID readToken(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TOKEN_KEY)) {
            try { return UUID.fromString(stack.getTag().getString(TOKEN_KEY)); } catch (Exception ignored) {}
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
}
