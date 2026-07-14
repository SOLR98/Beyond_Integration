package com.solr98.beyondintegration.feature.bind;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkBindingRegistry extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "beyond_integration_binding_registry";
    private static NetworkBindingRegistry instance;

    private final Map<Integer, NetworkEntries> networks = new ConcurrentHashMap<>();

    public static void initialize(ServerLevel level) {
        instance = level.getDataStorage().computeIfAbsent(
                NetworkBindingRegistry::load, NetworkBindingRegistry::new, NAME);
        LOGGER.info("[NBR] Initialized with {} network(s)", instance.networks.size());
    }

    public static NetworkBindingRegistry getInstance() { return instance; }

    // ========== Record methods ==========

    public static void recordBlockBind(int netId, BlockPos pos, String operator, @Nullable UUID operatorUuid) {
        recordBlockBind(netId, pos, operator, operatorUuid, null);
    }

    public static void recordBlockBind(int netId, BlockPos pos, String operator, @Nullable UUID operatorUuid, @Nullable String targetName) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        NetworkEntries ne = instance.networks.computeIfAbsent(netId, k -> new NetworkEntries());
        ne.blocks.put(pos, new EntryInfo(operator, operatorUuid, now, targetName));
        instance.setDirty();
    }

    public static void removeBlockBind(int netId, BlockPos pos) {
        if (instance == null) return;
        NetworkEntries ne = instance.networks.get(netId);
        if (ne != null && ne.blocks.remove(pos) != null) {
            instance.setDirty();
            cleanupEmpty(netId, ne);
            LOGGER.debug("[NBR] Removed block bind: netId={}, pos={}", netId, pos);
        }
    }

    public static void recordItemBind(int netId, String itemId, String operator, @Nullable UUID operatorUuid) {
        recordItemBind(netId, itemId, operator, operatorUuid, null);
    }

    public static void recordItemBind(int netId, String itemId, String operator, @Nullable UUID operatorUuid, @Nullable String targetName) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        NetworkEntries ne = instance.networks.computeIfAbsent(netId, k -> new NetworkEntries());
        ne.items.put(itemId, new EntryInfo(operator, operatorUuid, now, targetName));
        instance.setDirty();
    }

    public static void removeItemBind(int netId, String itemId) {
        if (instance == null) return;
        NetworkEntries ne = instance.networks.get(netId);
        if (ne != null && ne.items.remove(itemId) != null) {
            instance.setDirty();
            cleanupEmpty(netId, ne);
            LOGGER.debug("[NBR] Removed item bind: netId={}, item={}", netId, itemId);
        }
    }

    public static void recordVehicleBind(int netId, UUID vehicleUuid, String operator, @Nullable UUID operatorUuid) {
        recordVehicleBind(netId, vehicleUuid, operator, operatorUuid, null);
    }

    public static void recordVehicleBind(int netId, UUID vehicleUuid, String operator, @Nullable UUID operatorUuid, @Nullable String targetName) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        NetworkEntries ne = instance.networks.computeIfAbsent(netId, k -> new NetworkEntries());
        ne.vehicles.put(vehicleUuid, new EntryInfo(operator, operatorUuid, now, targetName));
        instance.setDirty();
    }

    public static void removeVehicleBind(int netId, UUID vehicleUuid) {
        if (instance == null) return;
        NetworkEntries ne = instance.networks.get(netId);
        if (ne != null && ne.vehicles.remove(vehicleUuid) != null) {
            instance.setDirty();
            cleanupEmpty(netId, ne);
            LOGGER.debug("[NBR] Removed vehicle bind: netId={}, vehicle={}", netId, vehicleUuid);
        }
    }

    public static void recordSentryBind(int netId, BlockPos pos, String operator, @Nullable UUID operatorUuid) {
        recordSentryBind(netId, pos, operator, operatorUuid, null);
    }

    public static void recordSentryBind(int netId, BlockPos pos, String operator, @Nullable UUID operatorUuid, @Nullable String targetName) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        NetworkEntries ne = instance.networks.computeIfAbsent(netId, k -> new NetworkEntries());
        ne.sentries.put(pos, new EntryInfo(operator, operatorUuid, now, targetName));
        instance.setDirty();
    }

    public static void removeSentryBind(int netId, BlockPos pos) {
        if (instance == null) return;
        NetworkEntries ne = instance.networks.get(netId);
        if (ne != null && ne.sentries.remove(pos) != null) {
            instance.setDirty();
            cleanupEmpty(netId, ne);
            LOGGER.debug("[NBR] Removed sentry bind: netId={}, pos={}", netId, pos);
        }
    }

    // ========== Query methods ==========

    @Nullable
    public static NetworkEntries getEntries(int netId) {
        if (instance == null) return null;
        return instance.networks.get(netId);
    }

    public static Set<Integer> getTrackedNetworks() {
        if (instance == null) return Set.of();
        return instance.networks.keySet();
    }

    // ========== Internal ==========

    private static void cleanupEmpty(int netId, NetworkEntries ne) {
        if (ne.isEmpty()) {
            instance.networks.remove(netId);
            instance.setDirty();
        }
    }

    public static class NetworkEntries {
        public final Map<BlockPos, EntryInfo> blocks = new ConcurrentHashMap<>();
        public final Map<String, EntryInfo> items = new ConcurrentHashMap<>();
        public final Map<UUID, EntryInfo> vehicles = new ConcurrentHashMap<>();
        public final Map<BlockPos, EntryInfo> sentries = new ConcurrentHashMap<>();

        public boolean isEmpty() {
            return blocks.isEmpty() && items.isEmpty() && vehicles.isEmpty() && sentries.isEmpty();
        }

        public int totalBindings() {
            return blocks.size() + items.size() + vehicles.size() + sentries.size();
        }
    }

    public static class EntryInfo {
        public final String operator;
        @Nullable public final UUID operatorUuid;
        public final long timestamp;
        @Nullable public final String targetName;

        public EntryInfo(String operator, @Nullable UUID operatorUuid, long timestamp) {
            this(operator, operatorUuid, timestamp, null);
        }

        public EntryInfo(String operator, @Nullable UUID operatorUuid, long timestamp, @Nullable String targetName) {
            this.operator = operator;
            this.operatorUuid = operatorUuid;
            this.timestamp = timestamp;
            this.targetName = targetName;
        }
    }

    // ========== NBT ==========

    public static NetworkBindingRegistry load(CompoundTag tag) {
        NetworkBindingRegistry reg = new NetworkBindingRegistry();
        CompoundTag netsTag = tag.getCompound("networks");
        for (String key : netsTag.getAllKeys()) {
            try {
                int netId = Integer.parseInt(key);
                CompoundTag netTag = netsTag.getCompound(key);
                NetworkEntries ne = new NetworkEntries();
                loadEntryMap(netTag.getList("blocks", 10), ne.blocks, true);
                loadEntryMap(netTag.getList("items", 10), ne.items, false);
                loadEntryUuidMap(netTag.getList("vehicles", 10), ne.vehicles);
                loadEntryMap(netTag.getList("sentries", 10), ne.sentries, true);
                if (!ne.isEmpty()) reg.networks.put(netId, ne);
            } catch (Exception ignored) {}
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag netsTag = new CompoundTag();
        for (var netEntry : networks.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            NetworkEntries ne = netEntry.getValue();
            saveEntryMap(netTag, "blocks", ne.blocks, true);
            saveEntryMap(netTag, "items", ne.items, false);
            saveEntryUuidMap(netTag, "vehicles", ne.vehicles);
            saveEntryMap(netTag, "sentries", ne.sentries, true);
            netsTag.put(String.valueOf(netEntry.getKey()), netTag);
        }
        tag.put("networks", netsTag);
        return tag;
    }

    private static void loadEntryMap(ListTag list, Map<?, EntryInfo> map, boolean isBlockPos) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            EntryInfo info = new EntryInfo(
                    e.getString("op"),
                    e.hasUUID("opUuid") ? e.getUUID("opUuid") : null,
                    e.getLong("t"),
                    e.contains("tn") ? e.getString("tn") : null);
            if (isBlockPos) {
                ((Map<BlockPos, EntryInfo>) map).put(BlockPos.of(e.getLong("pos")), info);
            } else {
                ((Map<String, EntryInfo>) map).put(e.getString("id"), info);
            }
        }
    }

    private static void loadEntryUuidMap(ListTag list, Map<UUID, EntryInfo> map) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            EntryInfo info = new EntryInfo(
                    e.getString("op"),
                    e.hasUUID("opUuid") ? e.getUUID("opUuid") : null,
                    e.getLong("t"),
                    e.contains("tn") ? e.getString("tn") : null);
            map.put(e.getUUID("uuid"), info);
        }
    }

    private static void saveEntryMap(CompoundTag netTag, String key, Map<?, EntryInfo> map, boolean isBlockPos) {
        ListTag list = new ListTag();
        for (var entry : map.entrySet()) {
            CompoundTag e = new CompoundTag();
            EntryInfo info = entry.getValue();
            e.putString("op", info.operator);
            if (info.operatorUuid != null) e.putUUID("opUuid", info.operatorUuid);
            e.putLong("t", info.timestamp);
            if (info.targetName != null) e.putString("tn", info.targetName);
            if (isBlockPos) {
                e.putLong("pos", ((BlockPos) entry.getKey()).asLong());
            } else if (entry.getKey() instanceof String) {
                e.putString("id", (String) entry.getKey());
            }
            list.add(e);
        }
        netTag.put(key, list);
    }

    private static void saveEntryUuidMap(CompoundTag netTag, String key, Map<UUID, EntryInfo> map) {
        ListTag list = new ListTag();
        for (var entry : map.entrySet()) {
            CompoundTag e = new CompoundTag();
            EntryInfo info = entry.getValue();
            e.putUUID("uuid", entry.getKey());
            e.putString("op", info.operator);
            if (info.operatorUuid != null) e.putUUID("opUuid", info.operatorUuid);
            e.putLong("t", info.timestamp);
            if (info.targetName != null) e.putString("tn", info.targetName);
            list.add(e);
        }
        netTag.put(key, list);
    }
}
