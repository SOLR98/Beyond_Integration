package com.solr98.beyondintegration.feature.bind;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BindingTokenManager extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "beyond_integration_binding_tokens";
    private static BindingTokenManager instance;

    private final Map<Integer, Map<UUID, UUID>> netToPlayerTokens = new ConcurrentHashMap<>();  // netId → playerUuid → token
    private final Map<UUID, TokenEntry> tokenToEntry = new ConcurrentHashMap<>();  // token → (netId, playerUuid)

    public static void initialize(ServerLevel level) {
        instance = level.getDataStorage().computeIfAbsent(
                BindingTokenManager::load, BindingTokenManager::new, NAME);
        LOGGER.info("[BTM] Initialized");
    }

    public BindingTokenManager() {}

    public static BindingTokenManager getInstance() { return instance; }

    // ========== Core API ==========

    /** Get or create token for a player on a network */
    public static UUID getOrCreateToken(int netId, UUID playerUuid) {
        BindingTokenManager inst = instance;
        if (inst == null) return UUID.randomUUID();

        Map<UUID, UUID> playerTokens = inst.netToPlayerTokens.computeIfAbsent(netId, k -> new ConcurrentHashMap<>());

        UUID existing = playerTokens.get(playerUuid);
        if (existing != null) return existing;

        UUID token = UUID.randomUUID();
        playerTokens.put(playerUuid, token);
        inst.tokenToEntry.put(token, new TokenEntry(netId, playerUuid));
        inst.setDirty();
        LOGGER.debug("[BTM] Created token for netId={}, player={}: {}", netId, playerUuid, token);
        return token;
    }

    /** Reset all tokens for a network, then create a fresh one for the given player */
    public static UUID resetToken(int netId, UUID playerUuid) {
        BindingTokenManager inst = instance;
        if (inst == null) return UUID.randomUUID();

        // Remove ALL existing tokens for this network (regardless of which player they belonged to)
        Map<UUID, UUID> playerTokens = inst.netToPlayerTokens.get(netId);
        if (playerTokens != null) {
            for (UUID token : playerTokens.values()) {
                inst.tokenToEntry.remove(token);
            }
            playerTokens.clear();
        }

        UUID newToken = UUID.randomUUID();
        inst.netToPlayerTokens.computeIfAbsent(netId, k -> new ConcurrentHashMap<>()).put(playerUuid, newToken);
        inst.tokenToEntry.put(newToken, new TokenEntry(netId, playerUuid));
        inst.setDirty();
        LOGGER.info("[BTM] Reset all tokens for netId={}, new token for player={}: {}", netId, playerUuid, newToken);
        return newToken;
    }

    /** Check if a token is valid for a given network */
    public static boolean isTokenValid(int netId, UUID token) {
        if (token == null) return true;
        BindingTokenManager inst = instance;
        if (inst == null) return true;
        TokenEntry entry = inst.tokenToEntry.get(token);
        if (entry == null) return false;
        return entry.netId() == netId;
    }

    /** Get token owner */
    public static UUID getTokenOwner(UUID token) {
        if (instance == null || token == null) return null;
        TokenEntry entry = instance.tokenToEntry.get(token);
        return entry != null ? entry.playerUuid() : null;
    }

    /** Get netId for a token */
    public static Integer getTokenNetId(UUID token) {
        if (instance == null || token == null) return null;
        TokenEntry entry = instance.tokenToEntry.get(token);
        return entry != null ? entry.netId() : null;
    }

    /** Get current token for a player on a network (without creating) */
    public static UUID getToken(int netId, UUID playerUuid) {
        if (instance == null) return null;
        Map<UUID, UUID> playerTokens = instance.netToPlayerTokens.get(netId);
        return playerTokens != null ? playerTokens.get(playerUuid) : null;
    }

    /** Get all tokens for a network */
    public static Map<UUID, UUID> getTokensForNetwork(int netId) {
        if (instance == null) return Map.of();
        return Map.copyOf(instance.netToPlayerTokens.getOrDefault(netId, Map.of()));
    }

    /** Remove all tokens for a network */
    public static void removeNetwork(int netId) {
        if (instance == null) return;
        Map<UUID, UUID> playerTokens = instance.netToPlayerTokens.remove(netId);
        if (playerTokens != null) {
            for (UUID token : playerTokens.values()) {
                instance.tokenToEntry.remove(token);
            }
            instance.setDirty();
        }
    }

    /** Remove token for a specific player on a network */
    public static void removePlayer(int netId, UUID playerUuid) {
        if (instance == null) return;
        Map<UUID, UUID> playerTokens = instance.netToPlayerTokens.get(netId);
        if (playerTokens != null) {
            UUID token = playerTokens.remove(playerUuid);
            if (token != null) {
                instance.tokenToEntry.remove(token);
                instance.setDirty();
                LOGGER.debug("[BTM] Removed token for netId={}, player={}", netId, playerUuid);
            }
        }
    }

    // ========== Token Entry ==========

    public record TokenEntry(int netId, UUID playerUuid) {}

    // ========== NBT ==========

    public static BindingTokenManager load(CompoundTag tag) {
        BindingTokenManager mgr = new BindingTokenManager();
        ListTag nets = tag.getList("networks", 10);
        for (int i = 0; i < nets.size(); i++) {
            CompoundTag netTag = nets.getCompound(i);
            int netId = netTag.getInt("id");
            ListTag players = netTag.getList("players", 10);
            Map<UUID, UUID> playerTokens = new ConcurrentHashMap<>();
            for (int j = 0; j < players.size(); j++) {
                CompoundTag pt = players.getCompound(j);
                UUID playerUuid = pt.getUUID("player");
                UUID token = pt.getUUID("token");
                playerTokens.put(playerUuid, token);
                mgr.tokenToEntry.put(token, new TokenEntry(netId, playerUuid));
            }
            if (!playerTokens.isEmpty()) mgr.netToPlayerTokens.put(netId, playerTokens);
        }
        LOGGER.info("[BTM] Loaded tokens for {} network(s)", mgr.netToPlayerTokens.size());
        return mgr;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag nets = new ListTag();
        for (var netEntry : netToPlayerTokens.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            netTag.putInt("id", netEntry.getKey());
            ListTag players = new ListTag();
            for (var pe : netEntry.getValue().entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putUUID("player", pe.getKey());
                pt.putUUID("token", pe.getValue());
                players.add(pt);
            }
            netTag.put("players", players);
            nets.add(netTag);
        }
        tag.put("networks", nets);
        LOGGER.debug("[BTM] Saved tokens for {} network(s)", netToPlayerTokens.size());
        return tag;
    }
}
