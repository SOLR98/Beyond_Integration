package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NetworkUtils {

    public static class NetworkStats {
        public int itemTypes = 0;
        public int fluidTypes = 0;
        public int energyTypes = 0;
        public BigInteger itemTotal = BigInteger.ZERO;
        public BigInteger fluidTotal = BigInteger.ZERO;
        public BigInteger energyTotal = BigInteger.ZERO;

        public int getTotalTypes() { return itemTypes + fluidTypes + energyTypes; }
        public BigInteger getTotalResources() { return itemTotal.add(fluidTotal).add(energyTotal); }
    }

    public static class PlayerList {
        public String owner = "";
        public final List<String> owners = new ArrayList<>();
        public final List<String> managers = new ArrayList<>();
        public final List<String> members = new ArrayList<>();
        public boolean hasPlayers() { return !owner.isEmpty() || !managers.isEmpty() || !members.isEmpty(); }
        public int getTotalPlayers() {
            int c = owner.isEmpty() ? 0 : 1;
            c += managers.size();
            c += members.size();
            return c;
        }
    }

    public static class NetInfo {
        public int netId;
        public int permissionWeight;
        public NetworkPermission permissionLevel;
        public String ownerName;
        public int playerCount;
        public int managerCount;
        public String netName;

        public NetInfo(int netId, int permissionWeight, NetworkPermission permissionLevel, String ownerName, int playerCount, int managerCount, String netName) {
            this.netId = netId;
            this.permissionWeight = permissionWeight;
            this.permissionLevel = permissionLevel;
            this.ownerName = ownerName;
            this.playerCount = playerCount;
            this.managerCount = managerCount;
            this.netName = netName;
        }
    }

    public static NetworkStats getNetworkStats(DimensionsNet net) {
        NetworkStats stats = new NetworkStats();
        if (net == null || net.deleted) return stats;
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            Object key = ka.key();
            long amount = ka.amount();
            if (key instanceof ItemStackKey) {
                stats.itemTypes++;
                stats.itemTotal = stats.itemTotal.add(BigInteger.valueOf(amount));
            } else if (key instanceof FluidStackKey) {
                stats.fluidTypes++;
                stats.fluidTotal = stats.fluidTotal.add(BigInteger.valueOf(amount));
            } else if (key instanceof EnergyStackKey) {
                stats.energyTypes++;
                stats.energyTotal = stats.energyTotal.add(BigInteger.valueOf(amount));
            }
        }
        return stats;
    }

    public static long getAvailableItemCount(DimensionsNet net, ItemStackKey key) {
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    public static long getAvailableFluidCount(DimensionsNet net, FluidStackKey key) {
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    public static long getAvailableEnergyCount(DimensionsNet net, EnergyStackKey key) {
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    public static boolean hasEnoughStorageForItem(DimensionsNet net, ItemStackKey key, long amountToAdd) {
        long current = net.getUnifiedStorage().getStackByKey(key).amount();
        long cap = net.getUnifiedStorage().getSlotCapacity(0);
        if (cap <= 0) cap = Long.MAX_VALUE;
        return current + amountToAdd <= cap;
    }

    public static boolean hasEnoughStorageForFluid(DimensionsNet net, FluidStackKey key, long amountToAdd) {
        long current = net.getUnifiedStorage().getStackByKey(key).amount();
        long cap = net.getUnifiedStorage().getSlotCapacity(0);
        if (cap <= 0) cap = Long.MAX_VALUE;
        return current + amountToAdd <= cap;
    }

    public static boolean hasEnoughStorageForEnergy(DimensionsNet net, EnergyStackKey key, long amountToAdd) {
        long current = net.getUnifiedStorage().getStackByKey(key).amount();
        long cap = net.getUnifiedStorage().getSlotCapacity(0);
        if (cap <= 0) cap = Long.MAX_VALUE;
        return current + amountToAdd <= cap;
    }

    public static PlayerList getNetworkPlayerList(DimensionsNet net, MinecraftServer server) {
        PlayerList list = new PlayerList();
        if (net == null || server == null) return list;

        UUID ownerUuid = net.getOwner();
        if (ownerUuid != null) {
            String name = CommandUtils.getPlayerNameByUUID(ownerUuid, server);
            if (name != null && !name.isEmpty()) {
                list.owner = name;
                list.owners.add(name);
            }
        }
        for (UUID muid : net.getManagers()) {
            if (ownerUuid != null && muid.equals(ownerUuid)) continue;
            String name = CommandUtils.getPlayerNameByUUID(muid, server);
            if (name != null && !name.isEmpty()) list.managers.add(name);
        }
        for (UUID puid : net.getPlayers()) {
            if (ownerUuid != null && puid.equals(ownerUuid)) continue;
            if (net.getManagers().contains(puid)) continue;
            String name = CommandUtils.getPlayerNameByUUID(puid, server);
            if (name != null && !name.isEmpty()) list.members.add(name);
        }
        return list;
    }

    public static NetworkPermission getPlayerPermissionLevel(ServerPlayer player, DimensionsNet net) {
        return NetworkPermission.fromPlayer(player, net);
    }

    public static String getPermissionLevelDisplay(NetworkPermission permissionLevel) {
        return permissionLevel.getDisplay();
    }

    public static List<DimensionsNet> getPlayerNetsPrimaryFirst(ServerPlayer player) {
        List<DimensionsNet> result = new ArrayList<>();
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary != null) result.add(primary);
        for (var net : DimensionsNet.getAllNetFromPlayer(player)) {
            if (primary == null || net.getId() != primary.getId()) result.add(net);
        }
        return result;
    }

    public static List<NetInfo> getPlayerNetworks(ServerPlayer player, MinecraftServer server) {
        List<NetInfo> networks = new ArrayList<>();
        if (server == null) return networks;
        for (int netId = 0; netId < 10000; netId++) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null && !net.deleted && net.getPlayers().contains(player.getUUID())) {
                int permissionWeight;
                NetworkPermission permissionLevel;
                if (net.isOwner(player.getUUID())) {
                    permissionWeight = 3;
                    permissionLevel = NetworkPermission.OWNER;
                } else if (net.isManager(player.getUUID())) {
                    permissionWeight = 2;
                    permissionLevel = NetworkPermission.MANAGER;
                } else {
                    permissionWeight = 1;
                    permissionLevel = NetworkPermission.MEMBER;
                }
                String ownerName = CommandUtils.getNetworkOwnerName(net, server);
                String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
                networks.add(new NetInfo(netId, permissionWeight, permissionLevel, ownerName, net.getPlayers().size(), net.getManagers().size(), netName));
            }
        }
        networks.sort((a, b) -> {
            int wc = Integer.compare(b.permissionWeight, a.permissionWeight);
            return wc != 0 ? wc : Integer.compare(a.netId, b.netId);
        });
        return networks;
    }

    public static int getCrystalRemainingTime(DimensionsNet net) {
        try {
            int crystalGenerateTime = com.wintercogs.beyonddimensions.config.ServerConfigRuntime.crystalGenerateTime;
            if (crystalGenerateTime <= 0) return -1;
            var field = DimensionsNet.class.getDeclaredField("currentTime");
            field.setAccessible(true);
            int elapsedTime = field.getInt(net);
            return Math.max(0, crystalGenerateTime * 20 - elapsedTime);
        } catch (Exception e) {
            return -1;
        }
    }
}
