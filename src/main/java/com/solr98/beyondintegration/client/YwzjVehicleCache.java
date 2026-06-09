package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

public enum YwzjVehicleCache {
    INSTANCE;

    private int netId = -1;
    private long energy = -1;
    private String networkName = "";
    private boolean hasData = false;
    private long lastUpdateTime = 0;
    private final Map<String, Long> ammoMap = new HashMap<>();
    private static final long TTL = 120000;

    public void update(int netId, long energy, String networkName, Map<String, Long> ammoMap) {
        this.netId = netId;
        this.energy = energy;
        this.networkName = networkName != null ? networkName : "";
        this.ammoMap.clear();
        if (ammoMap != null) this.ammoMap.putAll(ammoMap);
        this.hasData = true;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public int getNetId() { return netId; }
    public long getEnergy() { return energy; }
    public String getNetworkName() { return networkName; }
    public boolean hasData() { return hasData && System.currentTimeMillis() - lastUpdateTime < TTL; }

    public long getAmmoCount(String key) { return ammoMap.getOrDefault(key, 0L); }
    public Map<String, Long> getAmmoMap() { return new HashMap<>(ammoMap); }
    public boolean hasAnyAmmo() { return ammoMap.values().stream().anyMatch(v -> v > 0); }

    public void reset() {
        netId = -1;
        energy = -1;
        networkName = "";
        ammoMap.clear();
        hasData = false;
    }
}
