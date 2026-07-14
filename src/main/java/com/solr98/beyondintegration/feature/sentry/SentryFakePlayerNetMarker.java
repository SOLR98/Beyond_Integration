package com.solr98.beyondintegration.feature.sentry;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.WeakHashMap;

public final class SentryFakePlayerNetMarker {

    private static final Map<FakePlayer, Integer> bindings = new WeakHashMap<>();

    private SentryFakePlayerNetMarker() {}

    public static void mark(FakePlayer fp, int netId) {
        bindings.put(fp, netId);
    }

    public static boolean isMarked(FakePlayer fp) {
        return bindings.containsKey(fp);
    }

    public static int getNetId(FakePlayer fp) {
        return bindings.getOrDefault(fp, -1);
    }

    /** Get the actual network if bound, or null. Auto-unbinds on missing network. */
    public static DimensionsNet getNet(FakePlayer fp) {
        int id = getNetId(fp);
        if (id < 0) return null;
        DimensionsNet net = DimensionsNet.getNetFromId(id);
        if (net == null) bindings.remove(fp);
        return net;
    }

    public static void unmark(FakePlayer fp) {
        bindings.remove(fp);
    }

    public static FakePlayer getFakePlayerFromHandler(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                for (var entry : bindings.entrySet()) {
                    if (entry.getValue() == netId) return entry.getKey();
                }
            }
        }
        return null;
    }
}
