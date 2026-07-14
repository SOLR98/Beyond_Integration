package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

public class SuperbAmmoCache {

    private static final Object LOCK = new Object();

    // ── 玩家主网络 ──
    private static int netId = -1;
    private static String netName = "";
    private static Map<String, Long> ammo = new HashMap<>();
    private static long networkEnergy = -1;
    private static boolean hasData = false;
    private static boolean enchantSeparation = true;
    private static boolean requested = false;
    private static long lastUpdate = 0;
    private static long lastRequest = 0;
    private static final long UPDATE_INTERVAL = 5000;
    private static final long REQUEST_TIMEOUT = 2000;

    // ── 载具绑定网络 ──
    private static int vehicleNetId = -1;
    private static String vehicleNetName = "";
    private static Map<String, Long> vehicleAmmo = new HashMap<>();
    private static long vehicleEnergy = -1;
    private static boolean vehicleHasData = false;
    private static boolean vehicleRequested = false;
    private static long vehicleLastUpdate = 0;
    private static long vehicleLastRequest = 0;
    private static final long VEHICLE_UPDATE_INTERVAL = 5000;
    private static final long VEHICLE_REQUEST_TIMEOUT = 2000;

    // ── 玩家网络 ──
    public static void update(int netId, Map<String, Long> ammo, long energy, String name) {
        synchronized (LOCK) {
            SuperbAmmoCache.netId = netId;
            SuperbAmmoCache.netName = name != null ? name : "";
            SuperbAmmoCache.ammo = new HashMap<>(ammo);
            SuperbAmmoCache.networkEnergy = energy;
            SuperbAmmoCache.hasData = true;
            SuperbAmmoCache.requested = false;
            SuperbAmmoCache.lastUpdate = System.currentTimeMillis();
        }
    }

    public static boolean isStale() {
        synchronized (LOCK) {
            return System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL;
        }
    }

    public static boolean canRequest() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (!requested) return true;
            if (now - lastRequest > REQUEST_TIMEOUT) {
                requested = false;
                return true;
            }
            return false;
        }
    }

    public static void markRequested() {
        synchronized (LOCK) {
            SuperbAmmoCache.requested = true;
            SuperbAmmoCache.lastRequest = System.currentTimeMillis();
        }
    }

    public static String getNetworkName() {
        synchronized (LOCK) {
            return netName;
        }
    }

    public static long getCount(String ammoType) {
        synchronized (LOCK) {
            return ammo.getOrDefault(ammoType, 0L);
        }
    }

    public static long getNetworkEnergy() {
        synchronized (LOCK) {
            return networkEnergy;
        }
    }

    public static int getNetId() {
        synchronized (LOCK) {
            return netId;
        }
    }

    public static boolean hasData() {
        synchronized (LOCK) {
            return hasData;
        }
    }

    public static boolean isRequested() {
        synchronized (LOCK) {
            return requested;
        }
    }

    public static boolean getEnchantSeparation() {
        synchronized (LOCK) {
            return enchantSeparation;
        }
    }

    public static void setEnchantSeparation(boolean v) {
        synchronized (LOCK) {
            enchantSeparation = v;
        }
    }

    // ── 载具网络 ──
    public static void updateVehicle(int netId, Map<String, Long> ammo, long energy, String name) {
        synchronized (LOCK) {
            SuperbAmmoCache.vehicleNetId = netId;
            SuperbAmmoCache.vehicleNetName = name != null ? name : "";
            SuperbAmmoCache.vehicleAmmo = new HashMap<>(ammo);
            SuperbAmmoCache.vehicleEnergy = energy;
            SuperbAmmoCache.vehicleHasData = true;
            SuperbAmmoCache.vehicleRequested = false;
            SuperbAmmoCache.vehicleLastUpdate = System.currentTimeMillis();
        }
    }

    public static void resetVehicle() {
        synchronized (LOCK) {
            vehicleNetId = -1;
            vehicleNetName = "";
            vehicleAmmo.clear();
            vehicleEnergy = -1;
            vehicleHasData = false;
            vehicleRequested = false;
        }
    }

    public static long getVehicleCount(String ammoType) {
        synchronized (LOCK) {
            return vehicleAmmo.getOrDefault(ammoType, 0L);
        }
    }

    public static long getVehicleEnergy() {
        synchronized (LOCK) {
            return vehicleEnergy;
        }
    }

    public static int getVehicleNetId() {
        synchronized (LOCK) {
            return vehicleNetId;
        }
    }

    public static String getVehicleNetName() {
        synchronized (LOCK) {
            return vehicleNetName;
        }
    }

    public static boolean vehicleHasData() {
        synchronized (LOCK) {
            return vehicleHasData;
        }
    }

    public static boolean canVehicleRequest() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (!vehicleRequested) return true;
            if (now - vehicleLastRequest > VEHICLE_REQUEST_TIMEOUT) {
                vehicleRequested = false;
                return true;
            }
            return false;
        }
    }

    public static void markVehicleRequested() {
        synchronized (LOCK) {
            vehicleRequested = true;
            vehicleLastRequest = System.currentTimeMillis();
        }
    }

    public static boolean isVehicleStale() {
        synchronized (LOCK) {
            return System.currentTimeMillis() - vehicleLastUpdate > VEHICLE_UPDATE_INTERVAL;
        }
    }

    // ── 通用 ──
    public static void clear() {
        synchronized (LOCK) {
            netId = -1;
            ammo.clear();
            networkEnergy = -1;
            hasData = false;
            requested = false;

            vehicleNetId = -1;
            vehicleNetName = "";
            vehicleAmmo.clear();
            vehicleEnergy = -1;
            vehicleHasData = false;
            vehicleRequested = false;
        }
    }
}
