package com.solr98.beyondintegration.feature.ammo.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkAmmoData extends SavedData {

    private static final String NAME = "beyond_integration_attachments";
    private static final Map<Integer, Attachment> data = new ConcurrentHashMap<>();

    private static NetworkAmmoData instance;

    public static void initialize(ServerLevel level) {
        instance = level.getDataStorage().computeIfAbsent(NetworkAmmoData::load, NetworkAmmoData::new, NAME);
    }

    public static void markDirty() {
        if (instance != null) instance.setDirty();
    }

    public NetworkAmmoData() {}

    public static NetworkAmmoData load(CompoundTag tag) {
        CompoundTag networks = tag.getCompound("Networks");
        for (String key : networks.getAllKeys()) {
            int netId = Integer.parseInt(key);
            CompoundTag netTag = networks.getCompound(key);
            Attachment att = new Attachment();
            if (netTag.contains("SuperbAmmo")) {
                CompoundTag ammoTag = netTag.getCompound("SuperbAmmo");
                for (String ak : ammoTag.getAllKeys()) {
                    att.superbAmmo.put(ak, ammoTag.getLong(ak));
                }
            }
            if (netTag.contains("TaczCreativeTypes")) {
                CompoundTag ctTag = netTag.getCompound("TaczCreativeTypes");
                for (String tk : ctTag.getAllKeys()) {
                    att.taczCreativeTypeCounts.put(tk, ctTag.getInt(tk));
                }
            }
            att.enchantSeparation = netTag.contains("EnchantSeparation") && netTag.getBoolean("EnchantSeparation");
            data.put(netId, att);
        }
        if (instance == null) instance = new NetworkAmmoData();
        return instance;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag networks = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            CompoundTag ammoTag = new CompoundTag();
            for (var e : entry.getValue().superbAmmo.entrySet()) {
                ammoTag.putLong(e.getKey(), e.getValue());
            }
            netTag.put("SuperbAmmo", ammoTag);
            CompoundTag ctTag = new CompoundTag();
            for (var e : entry.getValue().taczCreativeTypeCounts.entrySet()) {
                ctTag.putInt(e.getKey(), e.getValue());
            }
            netTag.put("TaczCreativeTypes", ctTag);
            netTag.putBoolean("EnchantSeparation", entry.getValue().enchantSeparation);
            networks.put(String.valueOf(entry.getKey()), netTag);
        }
        tag.put("Networks", networks);
        return tag;
    }

    public static Attachment getOrCreate(int netId) {
        return data.computeIfAbsent(netId, k -> new Attachment());
    }

    public static void remove(int netId) {
        data.remove(netId);
    }

    public static class Attachment {
        private final Map<String, Long> superbAmmo = new HashMap<>();
        private final Map<String, Integer> taczCreativeTypeCounts = new HashMap<>();
        private boolean enchantSeparation = true;

        public Map<String, Long> getSuperbAmmo() { return superbAmmo; }
        public void setSuperbAmmo(Map<String, Long> map) {
            superbAmmo.clear();
            superbAmmo.putAll(map);
        }
        public Map<String, Integer> getTaczCreativeTypeCounts() { return taczCreativeTypeCounts; }
        public boolean isEnchantSeparation() { return enchantSeparation; }
        public void setEnchantSeparation(boolean v) { this.enchantSeparation = v; }
    }
}
