package com.solr98.beyondintegration.feature.bind;

import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.WeakHashMap;

public class BufferTracker {
    private static final Map<StackHandler, BufferContext> registry = new WeakHashMap<>();

    public static void register(StackHandler handler, int netId, BlockPos pos) {
        registry.put(handler, new BufferContext(netId, pos));
    }

    public static void unregister(StackHandler handler) {
        registry.remove(handler);
    }

    public static BufferContext get(StackHandler handler) {
        return registry.get(handler);
    }

    public record BufferContext(int netId, BlockPos pos) {}
}
