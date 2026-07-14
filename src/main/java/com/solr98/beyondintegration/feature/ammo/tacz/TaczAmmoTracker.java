package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczAmmoPushS2CPacket;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TaczAmmoTracker {

    private static final Map<Integer, TaczAmmoTracker> TRACKERS = new ConcurrentHashMap<>();

    private final int netId;
    private final Map<String, Integer> ammoCounts = new HashMap<>();
    private final Map<String, Integer> pending = new HashMap<>();
    private volatile boolean allCreative = false;
    private volatile boolean hasPending = false;
    private volatile boolean needsRescan = false;

    private TaczAmmoTracker(int netId) {
        this.netId = netId;
    }

    public static TaczAmmoTracker getOrCreate(DimensionsNet net) {
        int id = net.getId();
        return TRACKERS.computeIfAbsent(id, k -> {
            TaczAmmoTracker tracker = new TaczAmmoTracker(id);
            tracker.init(net);
            return tracker;
        });
    }

    public static void drainAllPending() {
        for (TaczAmmoTracker tracker : TRACKERS.values()) {
            tracker.drain();
        }
    }

    public static void clear() {
        TRACKERS.clear();
    }

    private void init(DimensionsNet net) {
        fullRescan(net);
        UnifiedStorage storage = net.getUnifiedStorage();
        storage.subscribeDeltaWeak(this, (self, key, size, insert) -> self.onDelta(net, key));
    }

    private void fullRescan(DimensionsNet net) {
        synchronized (ammoCounts) {
            allCreative = false;
            ammoCounts.clear();
            Map<String, Integer> fresh = TaczAmmoExtractor.countAllAmmoInNetwork(net);
            for (var entry : fresh.entrySet()) {
                if ("*".equals(entry.getKey())) {
                    allCreative = true;
                } else {
                    ammoCounts.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void onDelta(DimensionsNet net, IStackKey<?> key) {
        if (!(key instanceof ItemStackKey ik)) return;
        ItemStack stack = ik.getReadOnlyStack();

        if (stack.getItem() instanceof IAmmoBox) {
            needsRescan = true;
            return;
        }

        if (!(stack.getItem() instanceof IAmmo iAmmo)) return;
        ResourceLocation ammoId = iAmmo.getAmmoId(stack);
        if (ammoId == null) return;

        String idStr = ammoId.toString();
        KeyAmount found = net.getUnifiedStorage().getStackByKey(ik);
        int newCount = (int) Math.min(found.amount(), Integer.MAX_VALUE);

        synchronized (ammoCounts) {
            if (allCreative) return;
            if (newCount > 0) {
                ammoCounts.put(idStr, newCount);
            } else {
                ammoCounts.remove(idStr);
            }
        }
        markPending(idStr, newCount);
    }

    private void markPending(String ammoId, int count) {
        synchronized (pending) {
            pending.put(ammoId, count);
            hasPending = true;
        }
    }

    private void drain() {
        if (needsRescan) {
            needsRescan = false;
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) {
                fullRescan(net);
                Map<String, Integer> snapshot;
                synchronized (ammoCounts) {
                    snapshot = new HashMap<>(ammoCounts);
                    if (allCreative) snapshot.put("*", Integer.MAX_VALUE);
                }
                synchronized (pending) {
                    pending.clear();
                    hasPending = false;
                }
                pushToPlayers(snapshot);
            }
            return;
        }

        Map<String, Integer> snapshot;
        synchronized (pending) {
            if (!hasPending) return;
            snapshot = new HashMap<>(pending);
            pending.clear();
            hasPending = false;
        }
        pushToPlayers(snapshot);
    }

    private void pushToPlayers(Map<String, Integer> updates) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary != null && primary.getId() == this.netId) {
                PacketHandler.sendToPlayer(player, new TaczAmmoPushS2CPacket(updates));
            }
        }
    }

    public Map<String, Integer> getAllCounts() {
        synchronized (ammoCounts) {
            Map<String, Integer> result = new LinkedHashMap<>(ammoCounts);
            if (allCreative) {
                result.put("*", Integer.MAX_VALUE);
            }
            return result;
        }
    }
}
