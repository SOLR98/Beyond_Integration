package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.ids.BDItemIds;
import com.wintercogs.beyonddimensions.util.PlayerNameHelper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 网络工具命令
 * 功能：给予网络终端、附魔书、批量创建网络等
 */
public class NetworkToolsCommand {
    
    /**
     * 注册给予终端命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerGiveTerminal() {
        return Commands.literal("giveTerminal")
            .requires(source -> CommandUtils.hasOpPermission(source))
            // 默认：给予当前网络终端 x1
            .executes(ctx -> executeGiveTerminal(ctx, -1, 1))
            // 指定网络ID
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeGiveTerminal(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 1))
                // 指定数量
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeGiveTerminal(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                            IntegerArgumentType.getInteger(ctx, "count")))
                )
            );
    }
    
    /**
     * 注册给予附魔书命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerGiveEnchantedBooks() {
        return Commands.literal("giveEnchantedBooks")
            .requires(source -> CommandUtils.hasOpPermission(source))
            .then(Commands.argument("player", EntityArgument.player())
                // 默认：给予1本随机附魔书（1-3个附魔）
                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 1, "random", 1, 3))
                // 指定数量
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 
                            IntegerArgumentType.getInteger(ctx, "count"), "random", 1, 3))
                    // 随机附魔
                    .then(Commands.literal("random")
                        .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 
                                IntegerArgumentType.getInteger(ctx, "count"), "random", 1, 3))
                        // 指定最小附魔数量
                        .then(Commands.argument("minEnchants", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 
                                    IntegerArgumentType.getInteger(ctx, "count"), "random", 
                                    IntegerArgumentType.getInteger(ctx, "minEnchants"), 3))
                            // 指定最大附魔数量
                            .then(Commands.argument("maxEnchants", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 
                                        IntegerArgumentType.getInteger(ctx, "count"), "random", 
                                        IntegerArgumentType.getInteger(ctx, "minEnchants"), 
                                        IntegerArgumentType.getInteger(ctx, "maxEnchants")))
                            )
                        )
                    )
                    // 所有附魔
                    .then(Commands.literal("all")
                        .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 
                                IntegerArgumentType.getInteger(ctx, "count"), "all", 1, 1))
                    )
                )
            );
    }
    
    /**
     * 注册批量创建网络命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerBatchCreate() {
        return Commands.literal("batchCreate")
            .requires(source -> CommandUtils.hasOpPermission(source))
            .then(Commands.argument("player", EntityArgument.player())
                // 默认：创建1个网络，无限槽位容量，最大槽位数量
                .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), 1, Long.MAX_VALUE, Integer.MAX_VALUE))
                // 指定数量
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), 
                            IntegerArgumentType.getInteger(ctx, "count"), Long.MAX_VALUE, Integer.MAX_VALUE))
                    // 指定槽位容量
                    .then(Commands.argument("slotCapacity", LongArgumentType.longArg(1))
                        .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), 
                                IntegerArgumentType.getInteger(ctx, "count"), 
                                LongArgumentType.getLong(ctx, "slotCapacity"), Integer.MAX_VALUE))
                        // 指定槽位最大数量
                        .then(Commands.argument("slotMaxSize", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), 
                                    IntegerArgumentType.getInteger(ctx, "count"), 
                                    LongArgumentType.getLong(ctx, "slotCapacity"), 
                                    IntegerArgumentType.getInteger(ctx, "slotMaxSize")))
                        )
                    )
                )
            );
    }
    
    /**
     * 执行给予终端命令
     */
    private static int executeGiveTerminal(CommandContext<CommandSourceStack> ctx, int netId, int count) {
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
        
        // 获取网络终端物品
        Item terminalItem = BuiltInRegistries.ITEM.get(new ResourceLocation(BDConstants.MODID, BDItemIds.NET_TERMINAL_ITEM));
        if (terminalItem == null || terminalItem == Items.AIR) {
            source.sendFailure(OutputFormatter.createError("error.item_not_found"));
            return 0;
        }
        
        // 获取网络所有者名称
        String ownerName = CommandUtils.getNetworkOwnerName(net, source.getServer());
        
        // 创建终端物品
        for (int i = 0; i < count; i++) {
            ItemStack terminalStack = new ItemStack(terminalItem);
            
            // 设置终端名称
            String terminalName = CommandLang.get("network.giveTerminal.item_name", ownerName, actualNetId);
            terminalStack.setHoverName(Component.literal(terminalName));
            
            // 添加NBT描述
            CompoundTag tag = terminalStack.getOrCreateTag();
            String description = CommandLang.get("network.giveTerminal.item_description", ownerName, actualNetId);
            tag.putString("description", description);
            // 使用NetId字段（首字母大写），与Beyond Dimensions API保持一致
            tag.putInt("NetId", actualNetId);
            
            // 给予玩家
            if (!executor.getInventory().add(terminalStack)) {
                executor.drop(terminalStack, false);
            }
        }
        
        // 发送成功消息
        int finalNetId = actualNetId;
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.giveTerminal.success", finalNetId, ownerName, count)
        ).withStyle(ChatFormatting.GREEN), false);
        
        return count;
    }
    
    /**
     * 执行给予附魔书命令
     */
    private static int executeGiveEnchantedBooks(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, 
                                                int count, String enchantType, int minEnchants, int maxEnchants) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查服务器是否可用
        if (!PermissionChecker.checkServerAvailable(source)) {
            return 0;
        }
        
        Random random = new Random();
        
        // 获取所有附魔
        List<Enchantment> allEnchantments = BuiltInRegistries.ENCHANTMENT.stream()
                .collect(Collectors.toList());
        
        if (allEnchantments.isEmpty()) {
            source.sendFailure(OutputFormatter.createError("error.enchantment_book_not_implemented"));
            return 0;
        }
        
        // 检查"all"模式下的附魔数量
        if (enchantType.equals("all")) {
            int totalEnchantments = allEnchantments.size();
            source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.tools.giveEnchantedBooks.all_info", 
                    targetPlayer.getGameProfile().getName(), totalEnchantments)
            ).withStyle(ChatFormatting.YELLOW), false);
        }
        
        int givenCount = 0;
        
        for (int i = 0; i < count; i++) {
            ItemStack bookStack = new ItemStack(Items.ENCHANTED_BOOK);
            CompoundTag tag = bookStack.getOrCreateTag();
            ListTag enchantmentsList = new ListTag();
            
            int enchantmentCount;
            List<Enchantment> enchantmentsToAdd;
            
            if (enchantType.equals("all")) {
                // 给予所有附魔到一本书
                enchantmentCount = allEnchantments.size();
                enchantmentsToAdd = new ArrayList<>(allEnchantments);
            } else {
                // 随机数量
                enchantmentCount = minEnchants + random.nextInt(maxEnchants - minEnchants + 1);
                // 随机选择附魔
                enchantmentsToAdd = new ArrayList<>();
                Collections.shuffle(allEnchantments, random);
                for (int j = 0; j < Math.min(enchantmentCount, allEnchantments.size()); j++) {
                    enchantmentsToAdd.add(allEnchantments.get(j));
                }
            }
            
            for (Enchantment enchantment : enchantmentsToAdd) {
                // 强制添加所有附魔，忽略冲突和限制
                int maxLevel = enchantment.getMaxLevel();
                int level = maxLevel > 0 ? maxLevel : 1; // 使用最大等级
                
                CompoundTag enchantmentTag = new CompoundTag();
                enchantmentTag.putString("id", BuiltInRegistries.ENCHANTMENT.getKey(enchantment).toString());
                enchantmentTag.putShort("lvl", (short) level);
                enchantmentsList.add(enchantmentTag);
            }
            
            tag.put("StoredEnchantments", enchantmentsList);
            bookStack.setTag(tag);
            
            // 给予玩家
            if (!targetPlayer.getInventory().add(bookStack)) {
                targetPlayer.drop(bookStack, false);
            }
            
            givenCount++;
        }
        
        // 发送成功消息
        int finalGivenCount = givenCount;
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.tools.giveEnchantedBooks.success", 
                targetPlayer.getGameProfile().getName(), finalGivenCount)
        ).withStyle(ChatFormatting.GREEN), false);
        
        return givenCount;
    }
    
    /**
     * 执行批量创建网络命令
     */
    private static int executeBatchCreate(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, 
                                         int count, long slotCapacity, int slotMaxSize) {
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
        
        int createdCount = 0;
        List<String> failedNetworks = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            try {
                // 查找可用的网络ID
                int netId = findAvailableNetworkId(server);
                if (netId == -1) {
                    failedNetworks.add(CommandLang.get("network.batchCreate.error.no_available_id"));
                    break;
                }
                
                // 使用官方API创建网络
                DimensionsNet net = DimensionsNet.createNewNetForPlayer(targetPlayer, slotCapacity, slotMaxSize);
                
                if (net == null) {
                    failedNetworks.add(CommandLang.get("network.batchCreate.error.creation_failed", i + 1));
                    continue;
                }
                
                // 清除玩家的主网络（如果需要）
                DimensionsNet.clearPrimaryNetForPlayer(targetPlayer);
                
                createdCount++;
                
            } catch (Exception e) {
                failedNetworks.add(CommandLang.get("network.batchCreate.error.general_failure", i + 1, e.getMessage()));
            }
        }
        
        // 构建结果消息
        StringBuilder result = new StringBuilder();
        
        if (createdCount > 0) {
            result.append(CommandLang.get("network.batchCreate.success", createdCount))
                  .append(" 为玩家 ").append(targetPlayer.getGameProfile().getName())
                  .append("\n");
        }
        
        if (!failedNetworks.isEmpty()) {
            for (String failed : failedNetworks) {
                result.append(CommandLang.get("network.create.warning.failed_with_error", 
                        (createdCount + failedNetworks.indexOf(failed) + 1), 
                        targetPlayer.getGameProfile().getName(), failed))
                      .append("\n");
            }
        }
        
        if (createdCount == 0 && failedNetworks.isEmpty()) {
            result.append(CommandLang.get("network.batchCreate.error.no_networks_created")).append("\n");
        }
        
        source.sendSuccess(() -> Component.literal(result.toString()), false);
        return createdCount;
    }
    
    /**
     * 查找可用的网络ID
     */
    private static int findAvailableNetworkId(net.minecraft.server.MinecraftServer server) {
        for (int netId = 0; netId < 10000; netId++) {
            DimensionsNet net = server.overworld().getDataStorage().get(DimensionsNet::load, "BDNet_" + netId);
            if (net == null) {
                return netId;
            }
        }
        return -1;
    }
}