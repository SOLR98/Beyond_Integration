package com.solr98.beyondintegration.command.member;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 成员添加命令
 * 功能：批量添加成员或管理员到网络
 */
public class MemberAddCommand {
    
    /**
     * 注册添加成员命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerAddMembers() {
        return Commands.literal("addMembers")
            .then(Commands.argument("players", EntityArgument.players())
                // 默认：添加到当前玩家的主要网络
                .executes(ctx -> executeAddMembersToDefault(ctx, false))
                // 指定网络
                .then(Commands.literal("to")
                    .then(buildNetworkChain(5, false))));
    }
    
    /**
     * 注册添加管理员命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerAddManagers() {
        return Commands.literal("addManagers")
            .then(Commands.argument("players", EntityArgument.players())
                // 默认：添加到当前玩家的主要网络
                .executes(ctx -> executeAddMembersToDefault(ctx, true))
                // 指定网络
                .then(Commands.literal("to")
                    .then(buildNetworkChain(5, true))));
    }
    
    /**
     * 构建网络ID链（支持最多5个网络ID）
     */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildNetworkChain(int maxNetworks, boolean isManager) {
        if (maxNetworks <= 0) {
            return Commands.argument("netId1", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeAddMembers(ctx, isManager));
        }
        
        com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> builder = 
            Commands.argument("netId1", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeAddMembers(ctx, isManager));
        
        com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> recursive = 
            buildNetworkChainRecursive(2, maxNetworks, isManager);
        
        if (recursive != null) {
            builder = builder.then(recursive);
        }
        
        return builder;
    }
    
    /**
     * 递归构建网络ID链
     */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildNetworkChainRecursive(
            int current, int max, boolean isManager) {
        if (current > max) {
            // 达到最大网络数量，不再添加更多参数
            return null;
        }
        
        com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> builder = 
            Commands.argument("netId" + current, IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeAddMembers(ctx, isManager));
        
        com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> next = 
            buildNetworkChainRecursive(current + 1, max, isManager);
        
        if (next != null) {
            builder = builder.then(next);
        }
        
        return builder;
    }
    
    /**
     * 执行添加成员到默认网络（当前玩家的主要网络）
     */
    public static int executeAddMembersToDefault(CommandContext<CommandSourceStack> ctx, boolean isManager) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        
        // 检查服务器是否可用
        if (!PermissionChecker.checkServerAvailable(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取目标玩家
        Collection<ServerPlayer> targetPlayers = EntityArgument.getPlayers(ctx, "players");
        if (targetPlayers.isEmpty()) {
            source.sendFailure(Component.literal(CommandLang.get("network.batchAdd.no_players")));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        int netId = primaryNet.getId();
        
        // 检查执行者权限（必须是网络所有者或管理员）
        if (!PermissionChecker.checkNetworkManagementPermission(source, primaryNet, executor)) {
            return 0;
        }
        
        // 添加每个玩家到网络
        final int[] successCount = {0};
        final StringBuilder[] addedPlayers = {new StringBuilder()};
        
        for (ServerPlayer targetPlayer : targetPlayers) {
            if (addPlayerToNetwork(source, primaryNet, targetPlayer, isManager)) {
                successCount[0]++;
                if (addedPlayers[0].length() > 0) {
                    addedPlayers[0].append(", ");
                }
                addedPlayers[0].append(targetPlayer.getGameProfile().getName());
            }
        }
        
        // 发送结果消息
        String roleName = isManager ? 
                CommandLang.get("network.myNetworks.permission.manager") :
                CommandLang.get("network.myNetworks.permission.member");
        
        if (successCount[0] > 0) {
            source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.batchAddPlayer.success", 
                    successCount[0], targetPlayers.size(), roleName, netId) + 
                (addedPlayers[0].length() > 0 ? " (" + addedPlayers[0].toString() + ")" : "")
            ), false);
        } else {
            source.sendFailure(Component.literal(
                CommandLang.get("network.batchAddPlayer.failed", roleName, netId)
            ));
        }
        
        return successCount[0];
    }
    
    /**
     * 执行添加成员命令
     */
    public static int executeAddMembers(CommandContext<CommandSourceStack> ctx, boolean isManager) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        
        // 检查服务器是否可用
        if (!PermissionChecker.checkServerAvailable(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取目标玩家
        Collection<ServerPlayer> targetPlayers = EntityArgument.getPlayers(ctx, "players");
        if (targetPlayers.isEmpty()) {
            source.sendFailure(Component.literal(CommandLang.get("network.batchAdd.no_players")));
            return 0;
        }
        
        // 收集网络ID（从netId1到netId5）
        Set<Integer> networkIds = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            String argName = "netId" + i;
            try {
                int netId = IntegerArgumentType.getInteger(ctx, argName);
                networkIds.add(netId);
            } catch (IllegalArgumentException e) {
                // 参数不存在，停止收集
                break;
            }
        }
        
        if (networkIds.isEmpty()) {
            source.sendFailure(Component.literal(CommandLang.get("network.batchAddPlayer.no_networks")));
            return 0;
        }
        
        // 检查每个网络
        final int[] successCount = {0};
        int totalOperations = targetPlayers.size() * networkIds.size();
        final StringBuilder[] addedPlayers = {new StringBuilder()};
        
        for (int netId : networkIds) {
            // 检查网络是否存在
            DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) {
                continue;
            }
            
            // 检查执行者权限（必须是网络所有者或管理员）
            if (!PermissionChecker.checkNetworkManagementPermission(source, net, executor)) {
                continue;
            }
            
            // 添加每个玩家到网络
            for (ServerPlayer targetPlayer : targetPlayers) {
                if (addPlayerToNetwork(source, net, targetPlayer, isManager)) {
                    successCount[0]++;
                    if (addedPlayers[0].length() > 0) {
                        addedPlayers[0].append(", ");
                    }
                    addedPlayers[0].append(targetPlayer.getGameProfile().getName());
                }
            }
        }
        
        // 发送结果消息
        String roleName = isManager ? 
                CommandLang.get("network.myNetworks.permission.manager") :
                CommandLang.get("network.myNetworks.permission.member");
        
        if (successCount[0] > 0) {
            source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.batchAddToNetworks.success", 
                    successCount[0], totalOperations, roleName) + 
                (addedPlayers[0].length() > 0 ? " (" + addedPlayers[0].toString() + ")" : "")
            ), false);
        } else {
            source.sendFailure(Component.literal(
                CommandLang.get("network.batchAdd.failed", roleName)
            ));
        }
        
        return successCount[0];
    }
    
    /**
     * 添加玩家到网络
     */
    private static boolean addPlayerToNetwork(CommandSourceStack source, DimensionsNet net, 
                                             ServerPlayer player, boolean isManager) {
        try {
            UUID playerUuid = player.getUUID();
            
            // 检查玩家是否已经在网络中
            if (net.getPlayers().contains(playerUuid)) {
                // 如果已经是成员，检查是否需要升级为管理员
                if (isManager && !net.isManager(playerUuid)) {
                    net.addManager(playerUuid);
                    net.setDirty();
                    return true;
                }
                // 已经是正确角色，跳过
                return false;
            }
            
            // 添加玩家到网络
            net.addPlayer(playerUuid);
            
            // 如果是管理员，添加到管理员列表
            if (isManager) {
                net.addManager(playerUuid);
            }
            
            net.setDirty();
            return true;
            
        } catch (Exception e) {
            source.sendFailure(CommandLang.component("error.add_player_failed", e.getMessage()));
            return false;
        }
    }
}