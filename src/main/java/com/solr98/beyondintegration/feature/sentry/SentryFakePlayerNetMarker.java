package com.solr98.beyondintegration.feature.sentry;

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

/**
 * Manages the binding between FakePlayers (used by Create Sentry, TACZ guns) and BD network IDs.
 * <p>
 * FakePlayers need to know which BD network to draw ammo from. This class provides:
 * <ul>
 *   <li>Marking/unmarking a FakePlayer with a network ID</li>
 *   <li>Resolving a FakePlayer back to its bound {@link DimensionsNet}</li>
 *   <li>Deriving network ID from ammo boxes (for Sentry integration)</li>
 *   <li>Bi-directional lookup: {@code IItemHandler → FakePlayer} (for gun mixins)</li>
 * </ul>
 * <p>
 * Memory safety: uses {@link WeakHashMap} for the capability cache so stale entries
 * are automatically collected when FakePlayers are garbage collected.
 */
public class SentryFakePlayerNetMarker {

    private static final Map<IItemHandler, FakePlayer> handlerToFakePlayer = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<IItemHandler, FakePlayer> capCache = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<com.solr98.beyondintegration.handler.IFakePlayerNetId, Integer> fpNetIdMap = Collections.synchronizedMap(new WeakHashMap<>());

    public static void mark(FakePlayer fp, int netId) {
        if (fp instanceof com.solr98.beyondintegration.handler.IFakePlayerNetId marker) {
            marker.beyond$setNetId(netId);
            fpNetIdMap.put(marker, netId);
        }
    }

    public static void unmark(FakePlayer fp) {
        if (fp instanceof com.solr98.beyondintegration.handler.IFakePlayerNetId marker) {
            marker.beyond$setNetId(-1);
            fpNetIdMap.remove(marker);
        }
    }

    public static int getNetId(FakePlayer fp) {
        if (fp instanceof com.solr98.beyondintegration.handler.IFakePlayerNetId marker) {
            return marker.beyond$getNetId();
        }
        return -1;
    }

    @Nullable
    public static DimensionsNet getNet(FakePlayer fp) {
        int id = getNetId(fp);
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }

    public static boolean isMarked(FakePlayer fp) {
        if (fp instanceof com.solr98.beyondintegration.handler.IFakePlayerNetId marker) {
            return marker.beyond$getNetId() >= 0;
        }
        return false;
    }

    public static void markFromBoxes(FakePlayer fp, List<ItemStack> boxes) {
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

    @Nullable
    public static FakePlayer getFakePlayerFromHandler(IItemHandler handler) {
        if (handler instanceof Inventory inv && inv.player instanceof FakePlayer fp) {
            return fp;
        }
        FakePlayer cached = capCache.get(handler);
        if (cached != null) return cached;
        FakePlayer direct = handlerToFakePlayer.get(handler);
        if (direct != null) return direct;
        synchronized (fpNetIdMap) {
            for (var entry : fpNetIdMap.entrySet()) {
            var fpId = entry.getKey();
            if (!(fpId instanceof FakePlayer fp)) continue;
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
        }
        return null;
    }
}

