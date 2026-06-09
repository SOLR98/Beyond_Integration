package com.solr98.beyondintegration.command.util;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigInteger;
import java.util.*;

/**
 * 网络工具类
 * 提供网络操作相关功能
 */
public class NetworkUtils {
    
    /**
     * 获取网络统计信息
     */
    public static NetworkStats getNetworkStats(DimensionsNet net) {
        NetworkStats stats = new NetworkStats();
        
        if (net == null || net.deleted) {
            return stats;
        }
        
        // 统计不同类型的资源
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
    
    /**
     * 获取网络中物品的可用数量
     */
    public static long getAvailableItemCount(DimensionsNet net, ItemStackKey key) {
        KeyAmount stack = net.getUnifiedStorage().getStackByKey(key);
        return stack.amount();
    }
    
    /**
     * 获取网络中流体的可用数量
     */
    public static long getAvailableFluidCount(DimensionsNet net, FluidStackKey key) {
        KeyAmount stack = net.getUnifiedStorage().getStackByKey(key);
        return stack.amount();
    }
    
    /**
     * 获取网络中能量的可用数量
     */
    public static long getAvailableEnergyCount(DimensionsNet net, EnergyStackKey key) {
        KeyAmount stack = net.getUnifiedStorage().getStackByKey(key);
        return stack.amount();
    }
    
    /**
     * 检查网络是否有足够的存储空间（物品）
     */
    public static boolean hasEnoughStorageForItem(DimensionsNet net, ItemStackKey key, long amountToAdd) {
        KeyAmount currentStack = net.getUnifiedStorage().getStackByKey(key);
        long currentAmount = currentStack.amount();
        long slotCapacity = net.getUnifiedStorage().getSlotCapacity(0);
        
        if (slotCapacity <= 0) {
            slotCapacity = Long.MAX_VALUE;
        }
        
        return currentAmount + amountToAdd <= slotCapacity;
    }
    
    /**
     * 检查网络是否有足够的存储空间（流体）
     */
    public static boolean hasEnoughStorageForFluid(DimensionsNet net, FluidStackKey key, long amountToAdd) {
        KeyAmount currentStack = net.getUnifiedStorage().getStackByKey(key);
        long currentAmount = currentStack.amount();
        long slotCapacity = net.getUnifiedStorage().getSlotCapacity(0);
        
        if (slotCapacity <= 0) {
            slotCapacity = Long.MAX_VALUE;
        }
        
        return currentAmount + amountToAdd <= slotCapacity;
    }
    
    /**
     * 检查网络是否有足够的存储空间（能量）
     */
    public static boolean hasEnoughStorageForEnergy(DimensionsNet net, EnergyStackKey key, long amountToAdd) {
        KeyAmount currentStack = net.getUnifiedStorage().getStackByKey(key);
        long currentAmount = currentStack.amount();
        long slotCapacity = net.getUnifiedStorage().getSlotCapacity(0);
        
        if (slotCapacity <= 0) {
            slotCapacity = Long.MAX_VALUE;
        }
        
        return currentAmount + amountToAdd <= slotCapacity;
    }
    
    /**
     * 获取网络玩家列表（按权限分组）
     */
    public static PlayerList getNetworkPlayerList(DimensionsNet net, net.minecraft.server.MinecraftServer server) {
        PlayerList playerList = new PlayerList();
        
        if (net == null || server == null) {
            return playerList;
        }
        
        UUID ownerUuid = net.getOwner();
        
        // 添加所有者
        if (ownerUuid != null) {
            String ownerName = CommandUtils.getPlayerNameByUUID(ownerUuid, server);
            if (ownerName != null && !ownerName.isEmpty()) {
                playerList.owner = ownerName;
            }
        }
        
        // 添加管理员
        for (UUID managerUuid : net.getManagers()) {
            if (ownerUuid != null && managerUuid.equals(ownerUuid)) continue;
            
            String managerName = CommandUtils.getPlayerNameByUUID(managerUuid, server);
            if (managerName != null && !managerName.isEmpty()) {
                playerList.managers.add(managerName);
            }
        }
        
        // 添加普通成员
        for (UUID playerUuid : net.getPlayers()) {
            if (ownerUuid != null && playerUuid.equals(ownerUuid)) continue;
            if (net.getManagers().contains(playerUuid)) continue;
            
            String playerName = CommandUtils.getPlayerNameByUUID(playerUuid, server);
            if (playerName != null && !playerName.isEmpty()) {
                playerList.members.add(playerName);
            }
        }
        
        return playerList;
    }
    
    /**
     * 获取玩家在网络中的权限级别
     */
    public static String getPlayerPermissionLevel(ServerPlayer player, DimensionsNet net) {
        if (net == null) {
            return "none";
        }
        
        if (net.isOwner(player)) {
            return "owner";
        } else if (net.isManager(player)) {
            return "manager";
        } else if (net.getPlayers().contains(player.getUUID())) {
            return "member";
        } else {
            return "none";
        }
    }
    
    /**
     * 获取玩家权限级别的显示文本
     */
    public static String getPermissionLevelDisplay(String permissionLevel) {
        switch (permissionLevel) {
            case "owner":
                return com.solr98.beyondintegration.command.CommandLang.get("network.myNetworks.permission.owner");
            case "manager":
                return com.solr98.beyondintegration.command.CommandLang.get("network.myNetworks.permission.manager");
            case "member":
                return com.solr98.beyondintegration.command.CommandLang.get("network.myNetworks.permission.member");
            default:
                return com.solr98.beyondintegration.command.CommandLang.get("network.info.no_permission");
        }
    }
    
    /**
     * 获取玩家拥有权限的所有网络
     */
    public static List<NetworkInfo> getPlayerNetworks(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        List<NetworkInfo> networks = new ArrayList<>();

        if (server == null) {
            return networks;
        }

        // 使用 BD 玩家-网络索引，避免遍历 10000 次磁盘
        for (DimensionsNet net : DimensionsNet.getAllNetFromPlayer(player)) {
            if (net == null || net.deleted) continue;

            int permissionWeight;
            String permissionLevel;
            if (net.isOwner(player)) {
                permissionWeight = 3;
                permissionLevel = com.solr98.beyondintegration.command.CommandLang.get("network.myNetworks.permission.owner");
            } else if (net.isManager(player)) {
                permissionWeight = 2;
                permissionLevel = com.solr98.beyondintegration.command.CommandLang.get("network.myNetworks.permission.manager");
            } else {
                permissionWeight = 1;
                permissionLevel = com.solr98.beyondintegration.command.CommandLang.get("network.myNetworks.permission.member");
            }

            String ownerName = CommandUtils.getNetworkOwnerName(net, server);
            int playerCount = net.getPlayers().size();
            int managerCount = net.getManagers().size();

            networks.add(new NetworkInfo(net.getId(), permissionWeight, permissionLevel,
                    ownerName, playerCount, managerCount));
        }

        networks.sort((a, b) -> {
            int weightCompare = Integer.compare(b.permissionWeight, a.permissionWeight);
            if (weightCompare != 0) {
                return weightCompare;
            }
            return Integer.compare(a.netId, b.netId);
        });

        return networks;
    }
    
    /**
     * 获取结晶生成剩余时间
     */
    public static int getCrystalRemainingTime(DimensionsNet net) {
        try {
            // 获取结晶生成总时间（配置值 * 20转换为游戏刻）
            int crystalGenerateTime = com.wintercogs.beyonddimensions.config.ServerConfigRuntime.crystalGenerateTime;
            if (crystalGenerateTime <= 0) {
                return -1; // 结晶生成已禁用
            }
            
            // 获取当前已过去的时间
            java.lang.reflect.Field field = DimensionsNet.class.getDeclaredField("currentTime");
            field.setAccessible(true);
            int elapsedTime = field.getInt(net);
            
            // 计算剩余时间：总时间 - 已过去时间
            int totalTime = crystalGenerateTime * 20;
            return Math.max(0, totalTime - elapsedTime);
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * 网络统计信息类
     */
    public static class NetworkStats {
        public int itemTypes = 0;
        public int fluidTypes = 0;
        public int energyTypes = 0;
        public BigInteger itemTotal = BigInteger.ZERO;
        public BigInteger fluidTotal = BigInteger.ZERO;
        public BigInteger energyTotal = BigInteger.ZERO;
        
        public int getTotalTypes() {
            return itemTypes + fluidTypes + energyTypes;
        }
        
        public BigInteger getTotalResources() {
            return itemTotal.add(fluidTotal).add(energyTotal);
        }
    }
    
    /**
     * 玩家列表类
     */
    public static class PlayerList {
        public String owner = "";
        public List<String> managers = new ArrayList<>();
        public List<String> members = new ArrayList<>();
        
        public boolean hasPlayers() {
            return !owner.isEmpty() || !managers.isEmpty() || !members.isEmpty();
        }
        
        public int getTotalPlayers() {
            int count = owner.isEmpty() ? 0 : 1;
            count += managers.size();
            count += members.size();
            return count;
        }
    }
    
    /**
     * 网络信息类（用于排序和显示）
     */
    public static class NetworkInfo {
        public int netId;
        public int permissionWeight;
        public String permissionLevel;
        public String ownerName;
        public int playerCount;
        public int managerCount;
        
        public NetworkInfo(int netId, int permissionWeight, String permissionLevel, 
                          String ownerName, int playerCount, int managerCount) {
            this.netId = netId;
            this.permissionWeight = permissionWeight;
            this.permissionLevel = permissionLevel;
            this.ownerName = ownerName;
            this.playerCount = playerCount;
            this.managerCount = managerCount;
        }
    }
}