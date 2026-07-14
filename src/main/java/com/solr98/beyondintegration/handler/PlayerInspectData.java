package com.solr98.beyondintegration.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerInspectData extends SavedData {

    private static final String NAME = "beyond_integration_inspect_modes";
    private static PlayerInspectData instance;

    private final Map<UUID, Boolean> inspectMode = new ConcurrentHashMap<>();

    public static void initialize(ServerLevel level) {
        instance = level.getDataStorage().computeIfAbsent(
                PlayerInspectData::load, PlayerInspectData::new, NAME);
    }

    public PlayerInspectData() {}

    public static boolean isInspectMode(UUID playerUuid) {
        return instance != null && instance.inspectMode.getOrDefault(playerUuid, false);
    }

    public static void setInspectMode(UUID playerUuid, boolean enabled) {
        if (instance == null) return;
        if (enabled) {
            instance.inspectMode.put(playerUuid, true);
        } else {
            instance.inspectMode.remove(playerUuid);
        }
        instance.setDirty();
    }

    public static void toggle(UUID playerUuid) {
        setInspectMode(playerUuid, !isInspectMode(playerUuid));
    }

    public static PlayerInspectData load(CompoundTag tag) {
        PlayerInspectData data = new PlayerInspectData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                data.inspectMode.put(UUID.fromString(key), players.getBoolean(key));
            } catch (Exception ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (var entry : inspectMode.entrySet()) {
            players.putBoolean(entry.getKey().toString(), entry.getValue());
        }
        tag.put("players", players);
        return tag;
    }
}
