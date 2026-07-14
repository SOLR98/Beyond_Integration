package com.solr98.beyondintegration.command.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import com.solr98.beyondintegration.command.CommandLang;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

/**
 * 权限检查类
 * 提供统一的权限验证功能
 */
public class PermissionChecker {
    
    /**
     * 检查命令执行者是否是玩家
     */
    public static ServerPlayer checkPlayer(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player == null) {
            source.sendFailure(CommandLang.component("error.player_required"));
            throw new CommandSyntaxException(null, CommandLang.component("error.player_required"));
        }
        return player;
    }
    
    /**
     * 检查玩家是否有OP权限
     */
    public static boolean checkOpPermission(CommandSourceStack source) {
        if (!CommandUtils.hasOpPermission(source)) {
            source.sendFailure(CommandLang.component("error.op_required"));
            return false;
        }
        return true;
    }
    
    /**
     * 检查玩家是否有OP权限（用于查看其他玩家信息）
     */
    public static boolean checkOpPermissionForOthers(CommandSourceStack source, ServerPlayer targetPlayer) {
        if (!CommandUtils.hasOpPermission(source) && !source.getPlayer().getUUID().equals(targetPlayer.getUUID())) {
            source.sendFailure(CommandLang.component("error.op_required_for_others"));
            return false;
        }
        return true;
    }
    
    /**
     * 检查网络是否存在
     */
    public static DimensionsNet checkNetworkExists(CommandSourceStack source, int netId) {
        DimensionsNet net = CommandUtils.getNetOrFail(source, netId);
        if (net == null) {
            source.sendFailure(CommandLang.component("network.open.error.not_exist", netId));
            return null;
        }
        return net;
    }
    
    /**
     * 检查玩家是否有网络访问权限
     */
    public static boolean checkNetworkAccessPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        return CommandUtils.checkNetworkAccessPermission(source, net, player);
    }
    
    /**
     * 检查玩家是否有网络管理权限
     */
    public static boolean checkNetworkManagementPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        return CommandUtils.checkNetworkManagementPermission(source, net, player);
    }
    
    /**
     * 检查玩家是否有添加管理员的权限
     */
    public static boolean checkAddManagerPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (CommandUtils.hasOpPermission(source)) {
            return true;
        }
        
        if (!CommandUtils.isNetworkOwner(player, net)) {
            source.sendFailure(CommandLang.component("network.batchAdd.error.owner_required", net.getId()));
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查玩家是否有添加成员的权限
     */
    public static boolean checkAddMemberPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (CommandUtils.hasOpPermission(source)) {
            return true;
        }
        
        if (!CommandUtils.hasNetworkManagementPermission(player, net)) {
            source.sendFailure(CommandLang.component("network.batchAdd.error.owner_or_manager_required", net.getId()));
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查玩家是否有移除成员的权限
     */
    public static boolean checkRemoveMemberPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        return checkAddMemberPermission(source, net, player);
    }
    
    /**
     * 检查网络ID是否有效
     */
    public static boolean checkNetworkIdValid(CommandSourceStack source, int netId) {
        if (!CommandUtils.isValidNetworkId(netId)) {
            source.sendFailure(CommandLang.component("error.invalid_network_id", netId));
            return false;
        }
        return true;
    }
    
    /**
     * 检查数量是否为正数
     */
    public static boolean checkAmountPositive(CommandSourceStack source, long amount) {
        if (amount <= 0) {
            source.sendFailure(CommandLang.component("error.amount_must_be_positive"));
            return false;
        }
        return true;
    }
    
    /**
     * 检查玩家列表是否为空
     */
    public static boolean checkPlayerListNotEmpty(CommandSourceStack source, java.util.List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) {
            source.sendFailure(CommandLang.component("network.batchAdd.no_players"));
            return false;
        }
        return true;
    }
    
    /**
     * 检查网络列表是否为空
     */
    public static boolean checkNetworkListNotEmpty(CommandSourceStack source, java.util.List<Integer> netIds) {
        if (netIds == null || netIds.isEmpty()) {
            source.sendFailure(CommandLang.component("network.batchAddPlayer.no_networks"));
            return false;
        }
        return true;
    }
    
    /**
     * 检查资源类型是否有效
     */
    public static boolean checkResourceTypeValid(CommandSourceStack source, String resourceType) {
        String[] validTypes = {"items", "fluids", "energy", "mixed", "all"};
        for (String validType : validTypes) {
            if (validType.equalsIgnoreCase(resourceType)) {
                return true;
            }
        }
        source.sendFailure(CommandLang.component("error.invalid_resource_type", resourceType));
        return false;
    }
    
    /**
     * 检查服务器是否可用
     */
    public static boolean checkServerAvailable(CommandSourceStack source) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(CommandLang.component("error.server_not_available"));
            return false;
        }
        return true;
    }
}