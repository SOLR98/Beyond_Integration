package com.solr98.beyondintegration.handler;

import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

public class ToastDispatcher {

    private static BiConsumer<ItemStack, Integer> delegate = (stack, count) -> {};

    public static void setDelegate(BiConsumer<ItemStack, Integer> d) {
        delegate = d;
    }

    public static void show(ItemStack stack, int count) {
        if (!stack.isEmpty() && count > 0) {
            delegate.accept(stack, count);
        }
    }
}
