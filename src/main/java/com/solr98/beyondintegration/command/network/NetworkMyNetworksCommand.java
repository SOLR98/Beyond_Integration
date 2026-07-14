package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
 * 我的网络命令
 * 功能：查看自己拥有权限的网络信息
 */
public class NetworkMyNetworksCommand {
    
    /**
     * 注册命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("myNetworks")
            // 默认：显示默认网络信息或列表
            .executes(ctx -> executeDefault(ctx, null))
            // 显示列表
            .then(Commands.literal("list")
                .executes(ctx -> executeList(ctx, null, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeList(ctx, null, IntegerArgumentType.getInteger(ctx, "page"))))
            )
            // 查看特定网络信息（与/bdtools network info相同）
            .then(Commands.literal("info")
                // 默认：显示当前玩家的主要网络信息
                .executes(ctx -> executeInfo(ctx, -1, null))
                // 指定网络ID
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                    .executes(ctx -> executeInfo(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null)))
            )
            // 向后兼容：直接使用网络ID（不推荐）
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeInfo(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null))
            );
    }
    
    /**
     * 执行默认命令（显示默认网络或列表）
     */
    private static int executeDefault(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer) {
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
        
        // /bdtools myNetworks 只显示当前玩家的网络，不支持查看其他玩家
        ServerPlayer playerToCheck = executor;
        
        // 获取玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(playerToCheck);
        if (primaryNet != null) {
            // 显示主要网络信息（使用executeInfo方法）
            return executeInfo(ctx, primaryNet.getId(), playerToCheck);
        } else {
            // 显示网络列表
            return executeList(ctx, playerToCheck, 1);
        }
    }
    
    /**
     * 执行列表命令
     */
    private static int executeList(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, int page) {
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
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // /bdtools myNetworks 只显示当前玩家的网络，不支持查看其他玩家
        ServerPlayer playerToCheck = executor;
        
        // 获取玩家有权限的网络列表
        List<NetworkInfo> networks = getPlayerNetworks(server, playerToCheck);
        int totalNetworks = networks.size();
        
        if (totalNetworks == 0) {
            source.sendSuccess(() -> Component.literal(CommandLang.get("network.myNetworks.none"))
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        
        // 获取每页显示数量
        int maxPerPage = com.solr98.beyondintegration.CommandConfig.SERVER.maxNetworksPerPage.get();
        int totalPages = (int) Math.ceil((double) totalNetworks / maxPerPage);
        
        // 确保页码有效
        if (page > totalPages && totalPages > 0) {
            page = totalPages;
        }
        
        int startIndex = (page - 1) * maxPerPage;
        
        // 构建输出消息
        MutableComponent message = buildNetworkListMessage(networks, playerToCheck, page, totalPages, totalNetworks, startIndex, maxPerPage);
        
        source.sendSuccess(() -> message, false);
        return totalNetworks;
    }
    
    /**
     * 执行信息命令（直接调用NetworkInfoCommand.execute，但targetPlayer为null）
     */
    private static int executeInfo(CommandContext<CommandSourceStack> ctx, int netId, ServerPlayer targetPlayer) {
        // 直接调用NetworkInfoCommand.execute方法
        // targetPlayer参数为null，因为/bdtools myNetworks只显示当前玩家的网络
        return NetworkInfoCommand.execute(ctx, netId, null);
    }
    
    /**
     * 获取玩家有权限的网络列表
     */
    private static List<NetworkInfo> getPlayerNetworks(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        List<NetworkInfo> networks = new ArrayList<>();
        
        // 扫描所有网络（0-9999）
        for (int netId = 0; netId < 10000; netId++) {
            DimensionsNet net = server.overworld().getDataStorage().get(DimensionsNet::load, "BDNet_" + netId);
            if (net != null && !net.deleted) {
                // 检查玩家是否在网络中
                if (net.getPlayers().contains(player.getUUID())) {
                    // 获取网络所有者名称
                    String ownerName = CommandUtils.getNetworkOwnerName(net, server);
                    
                    // 获取玩家权限级别
                    String permissionLevel;
                    if (net.isOwner(player)) {
                        permissionLevel = CommandLang.get("network.myNetworks.permission.owner");
                    } else if (net.isManager(player)) {
                        permissionLevel = CommandLang.get("network.myNetworks.permission.manager");
                    } else {
                        permissionLevel = CommandLang.get("network.myNetworks.permission.member");
                    }
                    
                    // 统计网络信息
                    int playerCount = net.getPlayers().size();
                    int managerCount = net.getManagers().size();
                    
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
        // /bdtools myNetworks 只显示当前玩家的网络
        message = message.append(OutputFormatter.createPagedTitle("network.myNetworks.title.self", page))
                .append(Component.literal("\n"));
        
        // 添加表头（CommandLang 双语）
        message = message.append(Component.literal(String.format("%-4s", "ID"))
                .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%-8s", CommandLang.get("network.list.permission")))
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%-16s", CommandLang.get("network.list.owner")))
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%3s", CommandLang.get("network.list.players")))
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%3s", CommandLang.get("network.list.managers")))
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"));
        
        // 添加网络列表
        int displayedCount = 0;
        for (int i = 0; i < networks.size(); i++) {
            if (i >= startIndex && displayedCount < maxPerPage) {
                NetworkInfo info = networks.get(i);
                message = message.append(formatNetworkInfo(info))
                        .append(Component.literal("\n"));
                displayedCount++;
            }
        }
        
        // 添加分页导航
        if (totalPages > 1) {
            // /bdtools myNetworks 只显示当前玩家的网络
            String commandPrefix = "/bdtools myNetworks list";
            message = message.append(OutputFormatter.createPagination(page, totalPages, totalNetworks, commandPrefix));
        }
        
        return message;
    }
    
    /**
     * 格式化网络信息行
     */
    private static MutableComponent formatNetworkInfo(NetworkInfo info) {
        MutableComponent line = Component.empty();
        
        // 网络ID
        line = line.append(Component.literal(String.format("%-4d", info.netId))
                .withStyle(ChatFormatting.WHITE));
        
        // 权限级别
        ChatFormatting permissionColor = NetworkInfoCommand.getPermissionColor(info.permissionLevel);
        line = line.append(Component.literal(" | " + String.format("%-8s", info.permissionLevel))
                .withStyle(permissionColor));
        
        // 所有者名称
        line = line.append(Component.literal(" | " + String.format("%-16s", info.ownerName))
                .withStyle(ChatFormatting.AQUA));
        
        // 玩家数量
        line = line.append(Component.literal(" | " + String.format("%03d", info.playerCount))
                .withStyle(ChatFormatting.GREEN));
        
        // 管理员数量
        line = line.append(Component.literal(" | " + String.format("%03d", info.managerCount))
                .withStyle(ChatFormatting.BLUE));
        
        // 删除标记
        if (info.deleted) {
            line = line.append(Component.literal(" " + CommandLang.get("network.list.deleted_mark"))
                    .withStyle(ChatFormatting.RED));
        }
        
        return line;
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