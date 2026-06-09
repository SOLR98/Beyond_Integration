package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerNetMarker {
    private static final Map<IItemHandler, FakePlayer> capCache = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<FakePlayer, Boolean> allFakePlayers = Collections.synchronizedMap(new ConcurrentHashMap<>());
    private static final Map<IItemHandler, FakePlayer> handlerToFakePlayer = new ConcurrentHashMap<>();

    public static void mark(FakePlayer fp, int netId) {
        ((IFakePlayerNetId) fp).beyond$setNetId(netId);
        cacheHandler(fp);
    }

    public static void unmark(FakePlayer fp) {
        ((IFakePlayerNetId) fp).beyond$setNetId(-1);
    }

    public static int getNetId(FakePlayer fp) {
        return ((IFakePlayerNetId) fp).beyond$getNetId();
    }

    @Nullable
    public static DimensionsNet getNet(FakePlayer fp) {
        int id = getNetId(fp);
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }

    public static boolean isMarked(FakePlayer fp) {
        return ((IFakePlayerNetId) fp).beyond$getNetId() >= 0;
    }

    public static void markFromBoxes(FakePlayer fp, List<ItemStack> boxes) {
        allFakePlayers.put(fp, true);
        for (ItemStack stack : boxes) {
            if (stack.isEmpty()) continue;
            int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                mark(fp, netId);
                return;
            }
        }
        unmark(fp);
    }

    private static void cacheHandler(FakePlayer fp) {
        if (fp.getInventory() instanceof IItemHandler handler) {
            capCache.put(handler, fp);
            handlerToFakePlayer.put(handler, fp);
        }
        var cap = fp.getCapability(Capabilities.ItemHandler.ENTITY);
        if (cap != null) {
            capCache.put(cap, fp);
            handlerToFakePlayer.put(cap, fp);
        }
        allFakePlayers.put(fp, true);
    }

    @Nullable
    public static FakePlayer getFakePlayerFromHandler(IItemHandler handler) {
        if (handler instanceof Inventory inv && inv.player instanceof FakePlayer fp) {
            return fp;
        }
        FakePlayer cached = capCache.get(handler);
        if (cached != null) return cached;
        FakePlayer direct = handlerToFakePlayer.get(handler);
        if (direct != null) return direct;
        for (FakePlayer fp : allFakePlayers.keySet()) {
            if (fp.getInventory() instanceof IItemHandler h && h == handler) {
                handlerToFakePlayer.put(handler, fp);
                return fp;
            }
            var cap = fp.getCapability(Capabilities.ItemHandler.ENTITY);
            if (cap != null && cap == handler) {
                handlerToFakePlayer.put(handler, fp);
                return fp;
            }
        }
        return null;
    }
}
