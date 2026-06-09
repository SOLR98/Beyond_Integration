package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.mixin.BDMenuAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class BDNetworkHelper {

    private static final Map<UUID, Integer> openMenuCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> inventoryScanCache = new ConcurrentHashMap<>();
    private static final long SCAN_COOLDOWN_MS = 2000;
    private static final Map<UUID, Long> lastScanTime = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String className = event.getContainer().getClass().getName();
        if (className.contains("DimensionsCraftMenuTerminal") || className.contains("DimensionsNetGUI")) {
            int netId = resolveNetFromMenu(player);
            if (netId >= 0) {
                openMenuCache.put(player.getUUID(), netId);
            }
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer) {
            openMenuCache.remove(event.getEntity().getUUID());
        }
    }

    private static int resolveNetFromMenu(ServerPlayer player) {
        var menu = player.containerMenu;
        if (!(menu instanceof BDMenuAccessor accessor)) return -1;
        BlockPos pos = accessor.getEntityPos();
        if (pos == null) return -1;
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof NetedBlockEntity neted) {
            DimensionsNet net = neted.getNet();
            return net != null ? net.getId() : -1;
        }
        return -1;
    }

    public static DimensionsNet findNetwork(ServerPlayer player) {
        UUID uuid = player.getUUID();

        Integer cachedId = openMenuCache.get(uuid);
        if (cachedId != null && cachedId >= 0) {
            DimensionsNet net = DimensionsNet.getNetFromId(cachedId);
            if (net != null) return net;
            openMenuCache.remove(uuid);
        }

        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary != null && openMenuCache.containsKey(uuid)) {
            return primary;
        }
        if (primary != null) return primary;

        long now = System.currentTimeMillis();
        Long lastScan = lastScanTime.get(uuid);
        if (lastScan != null && now - lastScan < SCAN_COOLDOWN_MS) {
            Integer cachedInventoryId = inventoryScanCache.get(uuid);
            if (cachedInventoryId != null && cachedInventoryId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(cachedInventoryId);
                if (net != null) return net;
            }
        }

        lastScanTime.put(uuid, now);
        DimensionsNet found = findTerminalInInventory(player);
        if (found != null) {
            inventoryScanCache.put(uuid, found.getId());
            return found;
        }
        inventoryScanCache.remove(uuid);
        return null;
    }

    public static DimensionsNet findTerminalInInventory(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) return net;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) return net;
            }
        }
        return null;
    }

    public static List<DimensionsNet> findAllTerminalsInInventory(ServerPlayer player) {
        List<DimensionsNet> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0 && seen.add(netId)) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) result.add(net);
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0 && seen.add(netId)) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) result.add(net);
            }
        }
        return result;
    }

    public static long countItemsDirect(DimensionsNet net, ItemStack target) {
        ItemStackKey key = new ItemStackKey(target);
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    public static void clearCache(UUID playerUuid) {
        openMenuCache.remove(playerUuid);
        inventoryScanCache.remove(playerUuid);
        lastScanTime.remove(playerUuid);
    }

    public static void clearAllCaches() {
        openMenuCache.clear();
        inventoryScanCache.clear();
        lastScanTime.clear();
    }
}
