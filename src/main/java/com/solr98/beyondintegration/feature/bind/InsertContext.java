package com.solr98.beyondintegration.feature.bind;

import net.minecraft.core.BlockPos;

/**
 * Thread-local context for tracking which machine is currently inserting into the network.
 * Used by BeforeInsertHandler to record machine meter data.
 */
public class InsertContext {
    private static final ThreadLocal<Context> current = new ThreadLocal<>();

    public static void set(String machineType, BlockPos pos) {
        current.set(new Context(machineType, pos));
    }

    public static Context get() {
        return current.get();
    }

    public static void clear() {
        current.remove();
    }

    public record Context(String machineType, BlockPos pos) {}
}
