package com.solr98.beyondintegration.command.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import com.solr98.beyondintegration.command.CommandLang;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.util.PlayerNameHelper;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 命令工具类
 * 提供通用的命令操作功能
 */
public class CommandUtils {
    
    // OP权限等级
    public static final int OP_LEVEL = 2;
    
    /**
     * 获取命令执行者玩家
     */
    public static ServerPlayer getExecutorPlayer(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }
    
    /**
     * 检查玩家是否有OP权限
     */
    public static boolean hasOpPermission(CommandSourceStack source) {
        return source.hasPermission(OP_LEVEL);
    }
    
    /**
     * 检查玩家是否在指定网络中
     */
    public static boolean isPlayerInNetwork(ServerPlayer player, DimensionsNet net) {
        return net != null && net.getPlayers().contains(player.getUUID());
    }
    
    /**
     * 检查玩家是否是网络所有者
     */
    public static boolean isNetworkOwner(ServerPlayer player, DimensionsNet net) {
        return net != null && net.isOwner(player);
    }
    
    /**
     * 检查玩家是否是网络管理员
     */
    public static boolean isNetworkManager(ServerPlayer player, DimensionsNet net) {
        return net != null && net.isManager(player);
    }
    
    /**
     * 检查玩家是否有网络管理权限（所有者或管理员）
     */
    public static boolean hasNetworkManagementPermission(ServerPlayer player, DimensionsNet net) {
        return isNetworkOwner(player, net) || isNetworkManager(player, net);
    }
    
    /**
     * 获取网络或失败
     */
    public static DimensionsNet getNetOrFail(CommandSourceStack source, int netId) {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            source.sendFailure(CommandLang.component("network.open.error.not_exist", netId));
            return null;
        }
        return net;
    }
    
    /**
     * 检查玩家是否有权限访问网络
     */
    public static boolean checkNetworkAccessPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (hasOpPermission(source)) {
            return true;
        }
        
        if (!isPlayerInNetwork(player, net)) {
            source.sendFailure(CommandLang.component("network.open.error.no_permission", 
                    player.getGameProfile().getName(), net.getId()));
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查玩家是否有网络管理权限
     */
    public static boolean checkNetworkManagementPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (hasOpPermission(source)) {
            return true;
        }
        
        if (!hasNetworkManagementPermission(player, net)) {
            source.sendFailure(CommandLang.component("network.open.error.no_permission_control", 
                    player.getGameProfile().getName(), net.getId()));
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取玩家名称（通过UUID）
     */
    public static String getPlayerNameByUUID(UUID playerUuid, net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            return PlayerNameHelper.getPlayerNameByUUID(playerUuid, server);
        }
        return CommandLang.get("network.info.unknown");
    }
    
    /**
     * 获取网络所有者名称
     */
    public static String getNetworkOwnerName(DimensionsNet net, net.minecraft.server.MinecraftServer server) {
        if (net == null || net.getOwner() == null) {
            return CommandLang.get("network.info.unknown");
        }
        
        String ownerName = getPlayerNameByUUID(net.getOwner(), server);
        return ownerName != null && !ownerName.isEmpty() ? ownerName : CommandLang.get("network.info.unknown");
    }
    
    /**
     * 格式化大数字为易读格式（使用国际单位制SI）
     */
    public static String formatBigNumber(BigInteger number) {
        // 使用国际单位制（SI）前缀
        if (number.compareTo(BigInteger.valueOf(1_000_000_000_000_000_000L)) >= 0) { // 10¹⁸
            // E (exa) - 艾，1,000,000,000,000,000,000
            return number.divide(BigInteger.valueOf(1_000_000_000_000_000_000L)) + "E";
        } else if (number.compareTo(BigInteger.valueOf(1_000_000_000_000_000L)) >= 0) { // 10¹⁵
            // P (peta) - 拍，1,000,000,000,000,000
            return number.divide(BigInteger.valueOf(1_000_000_000_000_000L)) + "P";
        } else if (number.compareTo(BigInteger.valueOf(1_000_000_000_000L)) >= 0) { // 10¹²
            // T (tera) - 太，1,000,000,000,000
            return number.divide(BigInteger.valueOf(1_000_000_000_000L)) + "T";
        } else if (number.compareTo(BigInteger.valueOf(1_000_000_000L)) >= 0) { // 10⁹
            // G (giga) - 吉，1,000,000,000（SI单位制使用G）
            return number.divide(BigInteger.valueOf(1_000_000_000L)) + "G";
        } else if (number.compareTo(BigInteger.valueOf(1_000_000L)) >= 0) { // 10⁶
            // M (mega) - 兆，1,000,000
            return number.divide(BigInteger.valueOf(1_000_000L)) + "M";
        } else if (number.compareTo(BigInteger.valueOf(1_000L)) >= 0) { // 10³
            // K (kilo) - 千，1,000
            return number.divide(BigInteger.valueOf(1_000L)) + "K";
        } else {
            return number.toString();
        }
    }
    
    /**
     * 格式化长整数为易读格式
     */
    public static String formatLongNumber(long number) {
        return formatBigNumber(BigInteger.valueOf(number));
    }
    
    /**
     * 获取物品名称
     */
    public static String getItemName(ItemStack stack) {
        if (stack.isEmpty()) {
            return CommandLang.get("display.empty_item");
        }
        
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            return id.toString();
        }
        
        return stack.getDisplayName().getString();
    }
    
    /**
     * 获取流体名称
     */
    public static String getFluidName(Fluid fluid) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        if (id != null) {
            return id.toString();
        }
        
        return fluid.getFluidType().getDescriptionId();
    }
    
    /**
     * 获取流体名称（通过流体堆栈）
     */
    public static String getFluidName(FluidStack fluidStack) {
        if (fluidStack.isEmpty()) {
            return CommandLang.get("display.empty_fluid");
        }
        
        return getFluidName(fluidStack.getFluid());
    }
    
    /**
     * 解析网络ID参数（支持整数或玩家选择）
     */
    public static int parseNetworkIdParameter(CommandSourceStack source, String param) {
        if (param == null || param.isEmpty()) {
            source.sendFailure(CommandLang.component("error.network_id_required"));
            throw new IllegalArgumentException(CommandLang.get("error.network_id_required"));
        }
        
        try {
            // 检查是否是玩家选择器
            if (param.startsWith("@")) {
                ServerPlayer player = null;
                
                if (param.equals("@p") || param.equals("@s")) {
                    // 当前玩家或自己
                    player = source.getPlayer();
                    if (player == null) {
                        source.sendFailure(CommandLang.component("error.player_required_for_command"));
                        throw new IllegalArgumentException(CommandLang.get("error.player_required_for_command"));
                    }
                } else if (param.startsWith("@")) {
                    // 其他选择器，暂时不支持
                    source.sendFailure(CommandLang.component("error.unsupported_player_selector", param));
                    throw new IllegalArgumentException(CommandLang.get("error.unsupported_player_selector", param));
                }
                
                if (player != null) {
                    // 获取玩家的主要网络ID
                    return getPlayerPrimaryNetworkId(player);
                }
                // 如果player为null，应该已经抛出了异常，但为了安全起见，这里也抛出异常
                source.sendFailure(CommandLang.component("error.cannot_parse_player_selector", param));
                throw new IllegalArgumentException(CommandLang.get("error.cannot_parse_player_selector", param));
            }
            
            // 检查是否是玩家名
            ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(param);
            if (player != null) {
                // 获取玩家的主要网络ID
                return getPlayerPrimaryNetworkId(player);
            }
            
            // 尝试解析为整数
            try {
                return Integer.parseInt(param);
            } catch (NumberFormatException e) {
                source.sendFailure(CommandLang.component("error.invalid_network_id_or_player", param));
                throw new IllegalArgumentException(CommandLang.get("error.invalid_network_id_or_player", param));
            }
        } catch (IllegalArgumentException e) {
            // 重新抛出异常，让命令处理器捕获
            throw e;
        }
    }
    
    /**
     * 获取玩家的主要网络ID
     */
    private static int getPlayerPrimaryNetworkId(ServerPlayer player) {
        // 使用Beyond Dimensions的API获取玩家的主要网络
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) {
            throw new IllegalArgumentException(CommandLang.get("error.player_not_in_any_network", player.getGameProfile().getName()));
        }
        
        return net.getId();
    }
    
    /**
     * 验证网络ID范围
     */
    public static boolean isValidNetworkId(int netId) {
        return netId >= 0 && netId < 10000;
    }
    
    /**
     * 获取有效的网络ID列表
     */
    public static List<Integer> getValidNetworkIds(List<Integer> netIds) {
        List<Integer> validIds = new ArrayList<>();
        for (int netId : netIds) {
            if (isValidNetworkId(netId)) {
                validIds.add(netId);
            }
        }
        return validIds;
    }
    
    /**
     * 解析玩家列表参数
     */
    public static List<ServerPlayer> parsePlayerList(CommandSourceStack source, String[] playerNames) {
        List<ServerPlayer> players = new ArrayList<>();
        for (String playerName : playerNames) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(playerName);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }
    
    /**
     * 检查字符串是否为有效玩家名
     */
    public static boolean isValidPlayerName(String name) {
        // Minecraft玩家名规则：3-16个字符，只包含字母、数字和下划线
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
        return pattern.matcher(name).matches();
    }
}