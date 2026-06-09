package com.solr98.beyondintegration.handler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;

public class NetworkAmmoData extends SavedData {
    private static final String DATA_NAME = "beyond_integration_data";
    private final Map<Integer, Map<String, Long>> networkAmmo = new HashMap<>();
    private final Map<Integer, Boolean> enchantSeparation = new HashMap<>();
    private final Map<Integer, java.util.Set<String>> networkCreativeTypes = new HashMap<>();

    public NetworkAmmoData() {}

    public static NetworkAmmoData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new NetworkAmmoData();
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(NetworkAmmoData::new, NetworkAmmoData::load), DATA_NAME);
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

    public java.util.Set<String> getCreativeTypesForNet(int netId) {
        return networkCreativeTypes.computeIfAbsent(netId, k -> new java.util.HashSet<>());
    }

    public void setCreativeTypesForNet(int netId, java.util.Set<String> types) {
        networkCreativeTypes.put(netId, new java.util.HashSet<>(types));
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

        return tag;
    }

    public static NetworkAmmoData load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkAmmoData data = new NetworkAmmoData();
        CompoundTag netsTag = tag.getCompound("networkAmmo");
        for (String netKey : netsTag.getAllKeys()) {
            int netId = Integer.parseInt(netKey);
            CompoundTag netTag = netsTag.getCompound(netKey);
            CompoundTag ammoTag = netTag.getCompound("ammo");
            Map<String, Long> map = new HashMap<>();
            for (String ammoKey : ammoTag.getAllKeys()) map.put(ammoKey, ammoTag.getLong(ammoKey));
            data.networkAmmo.put(netId, map);
        }

        if (tag.contains("enchantSeparation")) {
            CompoundTag esTag = tag.getCompound("enchantSeparation");
            for (String key : esTag.getAllKeys()) data.enchantSeparation.put(Integer.parseInt(key), esTag.getBoolean(key));
        }

        if (tag.contains("creativeTypes")) {
            CompoundTag ctTag = tag.getCompound("creativeTypes");
            for (String netKey : ctTag.getAllKeys()) {
                int netId = Integer.parseInt(netKey);
                CompoundTag perNet = ctTag.getCompound(netKey);
                int size = perNet.getInt("size");
                java.util.Set<String> types = new java.util.HashSet<>();
                for (int i = 0; i < size; i++) types.add(perNet.getString(String.valueOf(i)));
                if (!types.isEmpty()) data.networkCreativeTypes.put(netId, types);
            }
        }

        return data;
    }
}
