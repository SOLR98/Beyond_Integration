package com.solr98.beyondintegration.feature.ammo.common;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NetworkAmmoData extends SavedData {
    private static final String DATA_NAME = "beyond_integration_data";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<Integer, Map<String, Long>> networkAmmo = new HashMap<>();
    private final Map<Integer, Boolean> enchantSeparation = new HashMap<>();
    private final Map<Integer, Set<String>> networkCreativeTypes = new HashMap<>();
    private final Map<Integer, Boolean> ywzjCreativeAmmo = new HashMap<>();

    public NetworkAmmoData() {}

    public static NetworkAmmoData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new NetworkAmmoData();
        var overworld = server.overworld();
        if (overworld == null) return new NetworkAmmoData();
        return overworld.getDataStorage().computeIfAbsent(new Factory<>(NetworkAmmoData::new, NetworkAmmoData::load), DATA_NAME);
    }

    public Map<String, Long> getAmmoForNet(int netId) {
        return networkAmmo.computeIfAbsent(netId, k -> new HashMap<>());
    }

    public void setAmmoForNet(int netId, Map<String, Long> ammo) {
        networkAmmo.put(netId, new HashMap<>(ammo));
        setDirty();
    }

    public boolean getEnchantSeparation(int netId) {
        return enchantSeparation.getOrDefault(netId, true);
    }

    public void setEnchantSeparation(int netId, boolean v) {
        enchantSeparation.put(netId, v);
        setDirty();
    }

    public Set<String> getCreativeTypesForNet(int netId) {
        return networkCreativeTypes.computeIfAbsent(netId, k -> new HashSet<>());
    }

    public void setCreativeTypesForNet(int netId, Set<String> types) {
        networkCreativeTypes.put(netId, new HashSet<>(types));
        setDirty();
    }

    public boolean getYwzjCreativeAmmo(int netId) {
        return ywzjCreativeAmmo.getOrDefault(netId, false);
    }

    public void setYwzjCreativeAmmo(int netId, boolean v) {
        ywzjCreativeAmmo.put(netId, v);
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag netsTag = new CompoundTag();
        for (var netEntry : networkAmmo.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            CompoundTag ammoTag = new CompoundTag();
            for (var ammoEntry : netEntry.getValue().entrySet()) ammoTag.putLong(ammoEntry.getKey(), ammoEntry.getValue());
            netTag.put("ammo", ammoTag);
            netsTag.put(String.valueOf(netEntry.getKey()), netTag);
        }
        tag.put("networkAmmo", netsTag);

        CompoundTag esTag = new CompoundTag();
        for (var e : enchantSeparation.entrySet()) esTag.putBoolean(String.valueOf(e.getKey()), e.getValue());
        tag.put("enchantSeparation", esTag);

        CompoundTag ctTag = new CompoundTag();
        for (var e : networkCreativeTypes.entrySet()) {
            CompoundTag perNet = new CompoundTag();
            int idx = 0;
            for (String s : e.getValue()) perNet.putString(String.valueOf(idx++), s);
            perNet.putInt("size", e.getValue().size());
            ctTag.put(String.valueOf(e.getKey()), perNet);
        }
        tag.put("creativeTypes", ctTag);

        CompoundTag ycaTag = new CompoundTag();
        for (var e : ywzjCreativeAmmo.entrySet()) ycaTag.putBoolean(String.valueOf(e.getKey()), e.getValue());
        tag.put("ywzjCreativeAmmo", ycaTag);

        return tag;
    }

    public static NetworkAmmoData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkAmmoData data = new NetworkAmmoData();
        CompoundTag netsTag = tag.getCompound("networkAmmo");
        for (String netKey : netsTag.getAllKeys()) {
            try {
                int netId = Integer.parseInt(netKey);
                CompoundTag netTag = netsTag.getCompound(netKey);
                CompoundTag ammoTag = netTag.getCompound("ammo");
                Map<String, Long> map = new HashMap<>();
                for (String ammoKey : ammoTag.getAllKeys()) map.put(ammoKey, ammoTag.getLong(ammoKey));
                data.networkAmmo.put(netId, map);
            } catch (NumberFormatException e) {
                LOGGER.warn("[BD-Integration] Corrupted NBT key in networkAmmo: '{}' is not a valid netId", netKey);
            }
        }

        if (tag.contains("enchantSeparation")) {
            CompoundTag esTag = tag.getCompound("enchantSeparation");
            for (String key : esTag.getAllKeys()) {
                try { data.enchantSeparation.put(Integer.parseInt(key), esTag.getBoolean(key)); } catch (NumberFormatException e) {
                    LOGGER.warn("[BD-Integration] Corrupted NBT key in enchantSeparation: '{}' is not a valid netId", key);
                }
            }
        }

        if (tag.contains("creativeTypes")) {
            CompoundTag ctTag = tag.getCompound("creativeTypes");
            for (String netKey : ctTag.getAllKeys()) {
                try {
                    int netId = Integer.parseInt(netKey);
                    CompoundTag perNet = ctTag.getCompound(netKey);
                    int size = perNet.getInt("size");
                    Set<String> types = new HashSet<>();
                    for (int i = 0; i < size; i++) types.add(perNet.getString(String.valueOf(i)));
                    if (!types.isEmpty()) data.networkCreativeTypes.put(netId, types);
                } catch (NumberFormatException e) {
                    LOGGER.warn("[BD-Integration] Corrupted NBT key in creativeTypes: '{}' is not a valid netId", netKey);
                }
            }
        }

        if (tag.contains("ywzjCreativeAmmo")) {
            CompoundTag ycaTag = tag.getCompound("ywzjCreativeAmmo");
            for (String key : ycaTag.getAllKeys()) {
                try { data.ywzjCreativeAmmo.put(Integer.parseInt(key), ycaTag.getBoolean(key)); } catch (NumberFormatException e) {
                    LOGGER.warn("[BD-Integration] Corrupted NBT key in ywzjCreativeAmmo: '{}' is not a valid netId", key);
                }
            }
        }

        return data;
    }
}
