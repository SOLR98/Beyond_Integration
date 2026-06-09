package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.util.PlayerNameHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络列表命令
 * 功能：列出所有网络或指定玩家的网络
 */
public class NetworkListCommand {
    
    /**
     * 注册命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("list")
            .requires(source -> CommandUtils.hasOpPermission(source))
            // 默认：显示第1页，服务器所有网络
            .executes(ctx -> execute(ctx, null, 1))
            // 指定页码，服务器所有网络
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> execute(ctx, null, IntegerArgumentType.getInteger(ctx, "page"))))
            // 指定玩家，默认第1页
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player"), 1))
                // 指定玩家和页码
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player"), 
                            IntegerArgumentType.getInteger(ctx, "page")))));
    }
    
    /**
     * 执行命令
     */
    private static int execute(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, int page) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查服务器是否可用
        if (!PermissionChecker.checkServerAvailable(source)) {
            return 0;
        }
        
        // 获取服务器
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(OutputFormatter.createError("error.server_not_available"));
            return 0;
        }
        
        // 检查权限（查看其他玩家网络需要OP权限）
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        if (targetPlayer != null && !executor.getUUID().equals(targetPlayer.getUUID())) {
            if (!PermissionChecker.checkOpPermissionForOthers(source, targetPlayer)) {
                return 0;
            }
        }
        
        // 获取每页显示数量
        int maxPerPage = com.solr98.beyondintegration.CommandConfig.SERVER.maxNetworksPerPage.get();
        int startIndex = (page - 1) * maxPerPage;
        
        // 获取网络列表
        // 如果没有指定玩家，显示所有网络（null表示所有网络）
        ServerPlayer listPlayer = targetPlayer != null ? targetPlayer : null;
        List<NetworkInfo> networks = getNetworkList(server, listPlayer);
        int totalNetworks = networks.size();
        
        // 计算分页
        int totalPages = (int) Math.ceil((double) totalNetworks / maxPerPage);
        if (page > totalPages && totalPages > 0) {
            page = totalPages;
            startIndex = (page - 1) * maxPerPage;
        }
        
        // 构建输出消息
        MutableComponent message = buildNetworkListMessage(networks, listPlayer, 
                page, totalPages, totalNetworks, startIndex, maxPerPage);
        
        source.sendSuccess(() -> message, false);
        return totalNetworks;
    }
    
    /**
     * 获取网络列表
     */
    private static List<NetworkInfo> getNetworkList(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        List<NetworkInfo> networks = new ArrayList<>();
        
        // 扫描所有网络（0-9999）
        for (int netId = 0; netId < 10000; netId++) {
            DimensionsNet net = server.overworld().getDataStorage().get(DimensionsNet::load, "BDNet_" + netId);
            if (net != null && !net.deleted) {
                // 检查玩家是否在网络中（如果指定了玩家）
                if (player == null || net.getPlayers().contains(player.getUUID())) {
                    // 获取网络所有者名称
                    String ownerName = CommandUtils.getNetworkOwnerName(net, server);
                    
                    // 统计网络信息
                    int playerCount = net.getPlayers().size();
                    int managerCount = net.getManagers().size();
                    
                    // 获取玩家权限级别
                    String permissionLevel = "none";
                    if (player != null) {
                        if (net.isOwner(player)) {
                            permissionLevel = "owner";
                        } else if (net.isManager(player)) {
                            permissionLevel = "manager";
                        } else if (net.getPlayers().contains(player.getUUID())) {
                            permissionLevel = "member";
                        }
                    }
                    
                    networks.add(new NetworkInfo(netId, permissionLevel, ownerName, 
                            playerCount, managerCount, net.deleted));
                }
            }
        }
        
        return networks;
    }
    
    /**
     * 构建网络列表消息
     */
    private static MutableComponent buildNetworkListMessage(List<NetworkInfo> networks, ServerPlayer player, 
                                                           int page, int totalPages, int totalNetworks, 
                                                           int startIndex, int maxPerPage) {
        MutableComponent message = Component.empty();
        
        // 添加标题
        if (player != null) {
            String playerName = player.getGameProfile().getName();
            message = message.append(OutputFormatter.createPagedTitle("network.list.player_title", page, playerName))
                    .append(Component.literal("\n"));
        } else {
            message = message.append(OutputFormatter.createPagedTitle("network.list.all_title", page))
                    .append(Component.literal("\n"));
        }
        
        // 添加表头（与数据行对齐）
        message = message.append(Component.literal(String.format("%-4s", "ID"))
                .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%-16s", CommandLang.get("network.list.owner")))
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%3s", CommandLang.get("network.list.players")))
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%3s", CommandLang.get("network.list.managers")))
                        .withStyle(ChatFormatting.YELLOW));
        
        if (player != null) {
            message = message.append(Component.literal(" | "))
                    .append(Component.literal(String.format("%-3s", CommandLang.get("network.list.permission")))
                            .withStyle(ChatFormatting.YELLOW));
        }
        
        message = message.append(Component.literal("\n"));
        
        // 添加网络列表
        int displayedCount = 0;
        for (int i = 0; i < networks.size(); i++) {
            if (i >= startIndex && displayedCount < maxPerPage) {
                NetworkInfo info = networks.get(i);
                message = message.append(formatNetworkInfo(info, player != null))
                        .append(Component.literal("\n"));
                displayedCount++;
            }
        }
        
        // 如果没有网络
        if (displayedCount == 0) {
            message = message.append(Component.literal(CommandLang.get("network.list.none"))
                    .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n"));
        }
        
        // 添加分页导航
        if (totalPages > 1) {
            String commandPrefix = player != null ? 
                    "/bdtools network list " + player.getGameProfile().getName() : 
                    "/bdtools network list";
            message = message.append(OutputFormatter.createPagination(page, totalPages, totalNetworks, commandPrefix));
        }
        
        return message;
    }
    
    /**
     * 格式化网络信息行
     */
    private static MutableComponent formatNetworkInfo(NetworkInfo info, boolean showPermission) {
        MutableComponent line = Component.empty();
        
        // 网络ID
        line = line.append(Component.literal(String.format("%-4d", info.netId))
                .withStyle(net.minecraft.ChatFormatting.WHITE));
        
        // 所有者名称
        line = line.append(Component.literal(" | " + String.format("%-16s", info.ownerName))
                .withStyle(ChatFormatting.AQUA));
        
        // 玩家数量（用0填充）
        line = line.append(Component.literal(" | " + String.format("%03d", info.playerCount))
                .withStyle(ChatFormatting.GREEN));
        
        // 管理员数量（用0填充）
        line = line.append(Component.literal(" | " + String.format("%03d", info.managerCount))
                .withStyle(ChatFormatting.BLUE));
        
        // 权限级别（如果显示）
        if (showPermission) {
            String permissionText;
            if (!info.permissionLevel.equals("none")) {
                permissionText = getPermissionDisplayText(info.permissionLevel);
            } else {
                permissionText = "无";
            }
            line = line.append(Component.literal(" | " + String.format("%-3s", permissionText))
                    .withStyle(getPermissionColor(info.permissionLevel)));
        }
        
        // 删除标记
        if (info.deleted) {
            line = line.append(Component.literal(" " + CommandLang.get("network.list.deleted_mark"))
                    .withStyle(ChatFormatting.RED));
        }
        
        return line;
    }
    
    /**
     * 获取权限显示文本
     */
    private static String getPermissionDisplayText(String permissionLevel) {
        switch (permissionLevel) {
            case "owner":
                return CommandLang.get("network.myNetworks.permission.owner");
            case "manager":
                return CommandLang.get("network.myNetworks.permission.manager");
            case "member":
                return CommandLang.get("network.myNetworks.permission.member");
            default:
                return "无";
        }
    }
    
    /**
     * 获取权限颜色
     */
    private static ChatFormatting getPermissionColor(String permissionLevel) {
        switch (permissionLevel) {
            case "owner":
                return ChatFormatting.RED;
            case "manager":
                return ChatFormatting.BLUE;
            case "member":
                return ChatFormatting.GREEN;
            default:
                return ChatFormatting.GRAY;
        }
    }
    
    /**
     * 网络信息类
     */
    private static class NetworkInfo {
        int netId;
        String permissionLevel;
        String ownerName;
        int playerCount;
        int managerCount;
        boolean deleted;
        
        NetworkInfo(int netId, String permissionLevel, String ownerName, 
                   int playerCount, int managerCount, boolean deleted) {
            this.netId = netId;
            this.permissionLevel = permissionLevel;
            this.ownerName = ownerName;
            this.playerCount = playerCount;
            this.managerCount = managerCount;
            this.deleted = deleted;
        }
    }
}