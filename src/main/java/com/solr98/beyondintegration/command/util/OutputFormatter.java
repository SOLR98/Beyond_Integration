package com.solr98.beyondintegration.command.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import com.solr98.beyondintegration.command.CommandLang;

import java.math.BigInteger;
import java.util.List;

/**
 * 输出格式化类
 * 统一所有命令的输出格式
 */
public class OutputFormatter {
    
    /**
     * 创建标题格式输出
     */
    public static MutableComponent createTitle(String titleKey, Object... args) {
        String title = CommandLang.get(titleKey, args);
        return Component.literal(title)
                .withStyle(ChatFormatting.GOLD)
                .withStyle(ChatFormatting.BOLD);
    }
    
    /**
     * 创建带页码的标题（page 作为最后一个 %s 参数传入）
     */
    public static MutableComponent createPagedTitle(String titleKey, int page, Object... args) {
        Object[] formatArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, formatArgs, 0, args.length);
        formatArgs[args.length] = page;
        String title = CommandLang.get(titleKey, formatArgs);
        return Component.literal(title)
                .withStyle(ChatFormatting.GOLD)
                .withStyle(ChatFormatting.BOLD);
    }
    
    /**
     * 创建成功消息
     */
    public static MutableComponent createSuccess(String messageKey, Object... args) {
        return Component.literal(CommandLang.get(messageKey, args))
                .withStyle(ChatFormatting.GREEN);
    }
    
    /**
     * 创建错误消息
     */
    public static MutableComponent createError(String messageKey, Object... args) {
        return Component.literal(CommandLang.get(messageKey, args))
                .withStyle(ChatFormatting.RED);
    }
    
    /**
     * 创建警告消息
     */
    public static MutableComponent createWarning(String messageKey, Object... args) {
        return Component.literal(CommandLang.get(messageKey, args))
                .withStyle(ChatFormatting.YELLOW);
    }
    
    /**
     * 创建信息消息
     */
    public static MutableComponent createInfo(String messageKey, Object... args) {
        return Component.literal(CommandLang.get(messageKey, args))
                .withStyle(ChatFormatting.AQUA);
    }
    
    /**
     * 创建网络信息行
     */
    public static MutableComponent createNetworkInfoLine(int netId, String permissionLevel, 
                                                         String ownerName, int playerCount, int managerCount) {
        String info = CommandLang.get("network.myNetworks.info.format",
                netId, permissionLevel, ownerName, playerCount, managerCount);
        return Component.literal(info)
                .withStyle(ChatFormatting.WHITE);
    }
    
    /**
     * 创建可悬停文本组件
     */
    public static Component createHoverableText(String text, String hoverText) {
        return Component.literal(text).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                .withColor(ChatFormatting.WHITE));
    }
    
    /**
     * 创建可悬停资源类型组件
     */
    public static Component createHoverableResourceType(int count, String resourceType) {
        ChatFormatting color = count > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.literal(String.valueOf(count)).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(CommandLang.get("display.resource_type_count", resourceType, count))))
                .withColor(color));
    }
    
    /**
     * 创建可悬停物品数量组件
     */
    public static Component createHoverableItemCount(BigInteger count) {
        String displayText = CommandUtils.formatBigNumber(count);
        return Component.literal(displayText).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(CommandLang.get("display.total_items", count))))
                .withColor(ChatFormatting.YELLOW));
    }
    
    /**
     * 创建可悬停流体数量组件
     */
    public static Component createHoverableFluid(BigInteger amount) {
        String displayText = CommandUtils.formatBigNumber(amount);
        return Component.literal(displayText).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(CommandLang.get("display.fluid_total", amount))))
                .withColor(ChatFormatting.AQUA));
    }
    
    /**
     * 创建可悬停能量数量组件
     */
    public static Component createHoverableEnergy(BigInteger amount) {
        String displayText = CommandUtils.formatBigNumber(amount);
        return Component.literal(displayText).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(CommandLang.get("display.energy_total", amount))))
                .withColor(ChatFormatting.LIGHT_PURPLE));
    }
    
    /**
     * 创建可悬停数字组件
     */
    public static Component createHoverableNumber(int number, String description) {
        String formattedNumber = CommandUtils.formatBigNumber(BigInteger.valueOf(number));
        return Component.literal(formattedNumber).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(description + formattedNumber + " (" + number + ")")))
                .withColor(ChatFormatting.GOLD));
    }
    
    /**
     * 创建可悬停数字组件（支持long）
     */
    public static Component createHoverableNumber(long number, String description) {
        String formattedNumber = CommandUtils.formatBigNumber(BigInteger.valueOf(number));
        return Component.literal(formattedNumber).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(description + formattedNumber + " (" + number + ")")))
                .withColor(ChatFormatting.GOLD));
    }
    
    /**
     * 创建可悬停时间组件
     */
    public static Component createHoverableTime(int ticks) {
        if (ticks < 0) {
            // 结晶生成已禁用
            return Component.literal(CommandLang.get("display.disabled")).withStyle(style -> style
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        Component.literal(CommandLang.get("display.crystal_generation_disabled"))))
                    .withColor(ChatFormatting.GRAY));
        }
        
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int hours = minutes / 60;
        int days = hours / 24;
        
        // 格式化时间字符串
        String timeStr;
        if (days > 0) {
            timeStr = CommandLang.get("time.format.days_hours_minutes_seconds", 
                days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            timeStr = CommandLang.get("time.format.hours_minutes_seconds", 
                hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            timeStr = CommandLang.get("time.format.minutes_seconds", 
                minutes, seconds % 60);
        } else if (seconds > 0) {
            timeStr = CommandLang.get("time.format.seconds", seconds);
        } else {
            timeStr = CommandLang.get("time.format.less_than_second");
        }
        
        // 构建悬停文本
        String hoverText;
        if (ticks == 0) {
            hoverText = CommandLang.get("display.crystal_remaining_time");
        } else {
            hoverText = CommandLang.get("display.crystal_remaining_time", timeStr) + 
                       "\n" + CommandLang.get("display.crystal_time_tooltip") +
                       "\ntick: " + ticks;
        }
        
        // 根据剩余时间设置颜色
        ChatFormatting color;
        if (ticks == 0) {
            color = ChatFormatting.GOLD; // 即将生成，金色
        } else if (seconds < 30) {
            color = ChatFormatting.GREEN; // 少于30秒，绿色
        } else if (seconds < 300) {
            color = ChatFormatting.YELLOW; // 少于5分钟，黄色
        } else {
            color = ChatFormatting.AQUA; // 其他情况，青色
        }
        
        return Component.literal(timeStr).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal(hoverText)))
                .withColor(color));
    }
    
    /**
     * 创建分页导航组件
     */
    public static MutableComponent createPagination(int currentPage, int totalPages, int totalItems, String commandPrefix) {
        MutableComponent navigation = Component.empty();
        
        if (currentPage > 1) {
            navigation = navigation.append(
                    Component.literal("[" + CommandLang.get("network.list.previous") + "]")
                            .withStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            commandPrefix + " " + (currentPage - 1)))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal(CommandLang.get("pagination.click_to_page", currentPage - 1))))
                                    .withColor(ChatFormatting.GREEN)
                            )
            ).append(Component.literal(" "));
        }
        
        navigation = navigation.append(
                Component.literal("[" + CommandLang.get("network.list.page_with_total", currentPage, totalPages, totalItems) + "]")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.YELLOW)
                        )
        );
        
        if (currentPage < totalPages) {
            navigation = navigation.append(Component.literal(" ")).append(
                    Component.literal("[" + CommandLang.get("network.list.next") + "]")
                            .withStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            commandPrefix + " " + (currentPage + 1)))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal(CommandLang.get("pagination.click_to_page", currentPage + 1))))
                                    .withColor(ChatFormatting.GREEN)
                            )
            );
        }
        
        return navigation;
    }
    
    /**
     * 创建玩家列表输出（按权限分组着色）
     */
    public static MutableComponent createPlayerList(NetworkUtils.PlayerList playerList) {
        MutableComponent message = Component.empty();
        boolean firstPlayer = true;
        
        // 添加所有者（红色）
        if (!playerList.owner.isEmpty()) {
            message = message.append(Component.literal(playerList.owner)
                    .withStyle(ChatFormatting.RED));
            firstPlayer = false;
        }
        
        // 添加管理员（蓝色）
        for (String manager : playerList.managers) {
            if (!firstPlayer) {
                message = message.append(Component.literal(", "));
            }
            message = message.append(Component.literal(manager)
                    .withStyle(ChatFormatting.BLUE));
            firstPlayer = false;
        }
        
        // 添加普通成员（绿色）
        for (String member : playerList.members) {
            if (!firstPlayer) {
                message = message.append(Component.literal(", "));
            }
            message = message.append(Component.literal(member)
                    .withStyle(ChatFormatting.GREEN));
            firstPlayer = false;
        }
        
        if (firstPlayer) {
            message = message.append(Component.literal(CommandLang.get("network.info.no_players"))
                    .withStyle(ChatFormatting.GRAY));
        }
        
        return message;
    }
    
    /**
     * 创建物品显示组件（带数量和样式）
     */
    public static MutableComponent createItemDisplay(net.minecraft.world.item.ItemStack itemStack, long amount) {
        net.minecraft.world.item.ItemStack displayStack = itemStack.copy();
        displayStack.setCount((int) Math.min(amount, Integer.MAX_VALUE));
        
        return Component.literal("")
                .append(displayStack.getDisplayName())
                .append(Component.literal(" x" + amount)
                        .withStyle(ChatFormatting.GRAY));
    }
    
    /**
     * 创建流体显示组件（带容量和样式）
     */
    public static MutableComponent createFluidDisplay(net.minecraft.world.level.material.Fluid fluid, long amount) {
        net.minecraftforge.fluids.FluidStack fluidStack = new net.minecraftforge.fluids.FluidStack(fluid, (int) Math.min(amount, Integer.MAX_VALUE));
        
        return Component.literal("")
                .append(Component.literal(fluidStack.getDisplayName().getString()))
                .append(Component.literal(" " + amount + "mB")
                        .withStyle(ChatFormatting.BLUE));
    }
    
    /**
     * 创建能量显示组件（带数量和样式）
     */
    public static MutableComponent createEnergyDisplay(String energyType, long amount) {
        return Component.literal("")
                .append(Component.literal(energyType)
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" " + amount + "FE")
                        .withStyle(ChatFormatting.YELLOW));
    }
    
    /**
     * 创建列表项（带缩进）
     */
    public static MutableComponent createListItem(String text, int indentLevel) {
        String indent = "  ".repeat(indentLevel);
        return Component.literal(indent + text)
                .withStyle(ChatFormatting.WHITE);
    }
    
    /**
     * 创建统计信息行
     */
    public static MutableComponent createStatLine(String label, Object value, ChatFormatting valueColor) {
        return Component.literal(label)
                .append(Component.literal(value.toString())
                        .withStyle(valueColor))
                .withStyle(ChatFormatting.WHITE);
    }
}