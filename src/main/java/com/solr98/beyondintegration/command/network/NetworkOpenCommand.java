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
import net.minecraft.world.SimpleMenuProvider;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.client.gui.NetMenuType;
import com.wintercogs.beyonddimensions.common.init.BDMenus;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.NetControlMenu;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;

/**
 * 网络打开命令
 * 功能：打开网络界面
 */
public class NetworkOpenCommand {
    
    /**
     * 注册命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("open")
            // 默认：打开当前玩家的网络存储界面
            .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_MENU, null, true))
            // 指定网络ID
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_MENU, null, true))
                // 指定终端界面
                .then(Commands.literal("terminal")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_TERMINAL, null, true))
                )
                // 指定权限控制界面
                .then(Commands.literal("permission")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "permission", true))
                )
                .then(Commands.literal("control")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "control", true))
                )
                // 指定合成界面
                .then(Commands.literal("craft")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_MENU, null, true))
                )
            )
            // 指定终端界面（当前网络）
            .then(Commands.literal("terminal")
                .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_TERMINAL, null, true))
            )
            // 指定权限控制界面（当前网络）
            .then(Commands.literal("permission")
                .executes(ctx -> executeOpen(ctx, -1, null, "permission", true))
            )
            .then(Commands.literal("control")
                .executes(ctx -> executeOpen(ctx, -1, null, "control", true))
            )
            // 指定合成界面（当前网络）
            .then(Commands.literal("craft")
                .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_MENU, null, true))
            );
    }
    
    /**
     * 注册OP专用命令（可以打开任何网络）
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerOpenAny() {
        return Commands.literal("openAny")
            .requires(source -> CommandUtils.hasOpPermission(source))
            // 默认：打开当前玩家的网络存储界面
            .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_MENU, null, false))
            // 指定网络ID
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_MENU, null, false))
                // 指定终端界面
                .then(Commands.literal("terminal")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_TERMINAL, null, false))
                )
                // 指定权限控制界面
                .then(Commands.literal("permission")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "permission", false))
                )
                .then(Commands.literal("control")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "control", false))
                )
                // 指定合成界面
                .then(Commands.literal("craft")
                    .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_MENU, null, false))
                )
            )
            // 指定终端界面（当前网络）
            .then(Commands.literal("terminal")
                .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_TERMINAL, null, false))
            )
            // 指定权限控制界面（当前网络）
            .then(Commands.literal("permission")
                .executes(ctx -> executeOpen(ctx, -1, null, "permission", false))
            )
            .then(Commands.literal("control")
                .executes(ctx -> executeOpen(ctx, -1, null, "control", false))
            )
            // 指定合成界面（当前网络）
            .then(Commands.literal("craft")
                .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_MENU, null, false))
            );
    }
    
    /**
     * 执行打开命令
     */
    private static int executeOpen(CommandContext<CommandSourceStack> ctx, int netId, 
                                  NetMenuType menuType, String specialMenu, boolean checkPermission) {
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
        
        // 如果未指定网络ID，使用当前玩家的网络
        int actualNetId = netId;
        if (actualNetId == -1) {
            DimensionsNet tempNet = DimensionsNet.getNetFromPlayer(executor);
            if (tempNet == null) {
                source.sendFailure(OutputFormatter.createError("error.not_in_network"));
                return 0;
            }
            actualNetId = tempNet.getId();
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, actualNetId);
        if (net == null) {
            return 0;
        }
        
        // 检查权限（如果需要）
        if (checkPermission && !PermissionChecker.checkNetworkAccessPermission(source, net, executor)) {
            return 0;
        }
        
        // 打开界面
        try {
            boolean success = openNetworkGui(executor, net, menuType, specialMenu);
            
            if (success) {
                String menuName = getMenuName(menuType, specialMenu);
                int finalNetId = actualNetId;
                source.sendSuccess(() -> Component.literal(
                    CommandLang.get("network.open.success", executor.getGameProfile().getName(), finalNetId, menuName)
                ).withStyle(ChatFormatting.GREEN), false);
                return 1;
            } else {
                source.sendFailure(OutputFormatter.createError("network.open.error.general", 
                    CommandLang.get("network.open.error.failed")));
                return 0;
            }
            
        } catch (Exception e) {
            source.sendFailure(OutputFormatter.createError("network.open.error.general", e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 打开网络界面
     */
    private static boolean openNetworkGui(ServerPlayer player, DimensionsNet net, 
                                         NetMenuType menuType, String specialMenu) {
        try {
            if (specialMenu != null) {
                // 特殊界面：权限控制
            if (specialMenu.equals("permission") || specialMenu.equals("control")) {
                // 检查权限（需要所有者或管理员）
                if (!net.isOwner(player) && !net.isManager(player)) {
                    return false;
                }
                
                // 打开真正的权限控制界面
                player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, _player) -> 
                        new NetControlMenu(containerId, playerInventory),
                    Component.literal(CommandLang.get("network.open.menu.permission"))
                ));
                return true;
            }
            }
            
            // 标准界面
            if (menuType != null) {
                switch (menuType) {
                    case NET_MENU:
                        // 打开网络存储界面
                        player.openMenu(new SimpleMenuProvider(
                            (containerId, playerInventory, _player) -> 
                                new DimensionsNetMenu(
                                    BDMenus.Dimensions_Net_Menu.get(),
                                    containerId,
                                    playerInventory,
                                    net.getUnifiedStorage()
                                ),
                            Component.literal(CommandLang.get("network.open.menu.storage"))
                        ));
                        break;
                        
                    case NET_CRAFT_MENU:
                        // 打开合成界面
                        player.openMenu(new SimpleMenuProvider(
                            (containerId, playerInventory, _player) -> 
                                new DimensionsCraftMenu(containerId, playerInventory, 
                                    new net.minecraft.network.FriendlyByteBuf(
                                        io.netty.buffer.Unpooled.buffer())),
                            Component.literal(CommandLang.get("network.open.menu.crafting"))
                        ));
                        break;
                        
                    case NET_CRAFT_TERMINAL:
                        // 打开网络终端界面
                        player.openMenu(new SimpleMenuProvider(
                            (containerId, playerInventory, _player) -> 
                                new DimensionsNetMenu(
                                    BDMenus.Dimensions_Net_Menu.get(),
                                    containerId,
                                    playerInventory,
                                    net.getUnifiedStorage()
                                ),
                            Component.literal(CommandLang.get("network.open.menu.terminal"))
                        ));
                        break;
                        
                    default:
                        return false;
                }
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取界面名称
     */
    private static String getMenuName(NetMenuType menuType, String specialMenu) {
        if (specialMenu != null) {
            if (specialMenu.equals("permission") || specialMenu.equals("control")) {
                return CommandLang.get("network.open.menu.permission");
            }
        }
        
        if (menuType != null) {
            switch (menuType) {
                case NET_MENU:
                    return CommandLang.get("network.open.menu.storage");
                case NET_CRAFT_MENU:
                    return CommandLang.get("network.open.menu.crafting");
                case NET_CRAFT_TERMINAL:
                    return CommandLang.get("network.open.menu.terminal");
                default:
                    return "未知界面";
            }
        }
        
        return "网络界面";
    }
}