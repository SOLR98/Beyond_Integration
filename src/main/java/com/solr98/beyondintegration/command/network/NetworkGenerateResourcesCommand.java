package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 网络资源生成命令
 * 功能：向网络生成测试资源
 */
public class NetworkGenerateResourcesCommand {
    
    /**
     * 注册命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("generateResources")
            .requires(source -> CommandUtils.hasOpPermission(source))
            // 默认：生成100种混合资源到当前网络
            .executes(ctx -> execute(ctx, -1, 100, 100, 300, false, false, "mixed"))
            // 指定网络ID
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 100, 100, 300, false, false, "mixed"))
                // 指定类型数量
                .then(Commands.argument("typeCount", IntegerArgumentType.integer(1))
                    .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                            IntegerArgumentType.getInteger(ctx, "typeCount"), 100, 300, false, false, "mixed"))
                    // 指定最小数量
                    .then(Commands.argument("minAmount", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                IntegerArgumentType.getInteger(ctx, "minAmount"), 300, false, false, "mixed"))
                        // 指定最大数量
                        .then(Commands.argument("maxAmount", IntegerArgumentType.integer(1))
                            .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                    IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                    IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                    IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "mixed"))
                            // 物品类型
                            .then(Commands.literal("items")
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                        IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                        IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                        IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "items"))
                                .then(Commands.argument("withEnchantments", BoolArgumentType.bool())
                                    .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                            IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                            IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                            IntegerArgumentType.getInteger(ctx, "maxAmount"), 
                                            BoolArgumentType.getBool(ctx, "withEnchantments"), false, "items"))
                                    .then(Commands.argument("withNbt", BoolArgumentType.bool())
                                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                                IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                                IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                                IntegerArgumentType.getInteger(ctx, "maxAmount"), 
                                                BoolArgumentType.getBool(ctx, "withEnchantments"), 
                                                BoolArgumentType.getBool(ctx, "withNbt"), "items"))
                                    )
                                )
                            )
                            // 流体类型
                            .then(Commands.literal("fluids")
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                        IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                        IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                        IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "fluids"))
                            )
                            // 能量类型
                            .then(Commands.literal("energy")
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                        IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                        IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                        IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "energy"))
                            )
                            // 混合类型
                            .then(Commands.literal("mixed")
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                        IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                        IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                        IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "mixed"))
                                .then(Commands.argument("withEnchantments", BoolArgumentType.bool())
                                    .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                            IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                            IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                            IntegerArgumentType.getInteger(ctx, "maxAmount"), 
                                            BoolArgumentType.getBool(ctx, "withEnchantments"), false, "mixed"))
                                    .then(Commands.argument("withNbt", BoolArgumentType.bool())
                                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                                IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                                IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                                IntegerArgumentType.getInteger(ctx, "maxAmount"), 
                                                BoolArgumentType.getBool(ctx, "withEnchantments"), 
                                                BoolArgumentType.getBool(ctx, "withNbt"), "mixed"))
                                    )
                                )
                            )
                            // 全部类型
                            .then(Commands.literal("all")
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                        IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                        IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                        IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "all"))
                                .then(Commands.argument("withEnchantments", BoolArgumentType.bool())
                                    .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                            IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                            IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                            IntegerArgumentType.getInteger(ctx, "maxAmount"), 
                                            BoolArgumentType.getBool(ctx, "withEnchantments"), false, "all"))
                                    .then(Commands.argument("withNbt", BoolArgumentType.bool())
                                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                                                IntegerArgumentType.getInteger(ctx, "typeCount"), 
                                                IntegerArgumentType.getInteger(ctx, "minAmount"), 
                                                IntegerArgumentType.getInteger(ctx, "maxAmount"), 
                                                BoolArgumentType.getBool(ctx, "withEnchantments"), 
                                                BoolArgumentType.getBool(ctx, "withNbt"), "all"))
                                    )
                                )
                            )
                        )
                    )
                )
            );
    }
    
    /**
     * 执行命令
     */
    private static int execute(CommandContext<CommandSourceStack> ctx, int netId, int typeCount, 
                              int minAmount, int maxAmount, boolean withEnchantments, 
                              boolean withNbt, String resourceType) {
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
        
        // 如果未指定网络ID，使用当前玩家的网络
        if (netId == -1) {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(OutputFormatter.createError("error.player_required"));
                return 0;
            }
            
            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net == null) {
                source.sendFailure(OutputFormatter.createError("error.not_in_network"));
                return 0;
            }
            netId = net.getId();
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        // 检查权限（需要OP权限）
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 生成资源
        GenerationResult result = generateResources(net, typeCount, minAmount, maxAmount, 
                withEnchantments, withNbt, resourceType, server);
        
        // 发送结果消息
        sendGenerationResult(source, netId, result, resourceType);
        
        return (int) result.totalResources;
    }
    
    /**
     * 生成资源
     */
    private static GenerationResult generateResources(DimensionsNet net, int typeCount, 
                                                     int minAmount, int maxAmount, 
                                                     boolean withEnchantments, boolean withNbt,
                                                     String resourceType, net.minecraft.server.MinecraftServer server) {
        GenerationResult result = new GenerationResult();
        Random random = new Random();
        
        // 根据资源类型生成
        switch (resourceType.toLowerCase()) {
            case "items":
                generateItems(net, typeCount, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
                break;
            case "fluids":
                generateFluids(net, typeCount, minAmount, maxAmount, random, server, result);
                break;
            case "energy":
                generateEnergy(net, typeCount, minAmount, maxAmount, random, result);
                break;
            case "mixed":
                // 混合类型：按比例分配
                int itemCount = typeCount / 3;
                int fluidCount = typeCount / 3;
                int energyCount = typeCount - itemCount - fluidCount;
                
                if (itemCount > 0) generateItems(net, itemCount, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
                if (fluidCount > 0) generateFluids(net, fluidCount, minAmount, maxAmount, random, server, result);
                if (energyCount > 0) generateEnergy(net, energyCount, minAmount, maxAmount, random, result);
                break;
            case "all":
                // 所有类型：尽可能生成所有可用类型
                generateAllResources(net, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
                break;
        }
        
        return result;
    }
    
    /**
     * 生成物品
     */
    private static void generateItems(DimensionsNet net, int typeCount, int minAmount, int maxAmount,
                                     boolean withEnchantments, boolean withNbt, Random random,
                                     net.minecraft.server.MinecraftServer server, GenerationResult result) {
        // 获取所有物品
        List<Item> allItems = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
        Collections.shuffle(allItems, random);
        
        int generated = 0;
        for (Item item : allItems) {
            if (generated >= typeCount) break;
            
            // 跳过空气和无效物品
            if (item == Items.AIR || item == null) continue;
            
            try {
                // 创建物品堆栈
                ItemStack stack = new ItemStack(item);
                int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                
                // 添加附魔（如果启用）- 强制添加，不检查兼容性
                int enchantmentCount = 0;
                if (withEnchantments) {
                    enchantmentCount = addRandomEnchantments(stack, random);
                    if (enchantmentCount > 0) {
                        result.itemsWithEnchantments++;
                        result.totalEnchantments += enchantmentCount;
                    }
                }
                
                // 添加NBT（如果启用）
                boolean hasNbt = false;
                if (withNbt) {
                    addRandomNbt(stack, random);
                    hasNbt = true;
                    result.itemsWithNbt++;
                    // 估算NBT大小：800-1450字节，平均1125字节
                    result.estimatedNbtSize += 800 + random.nextInt(650);
                }
                
                // 插入到网络
                ItemStackKey key = new ItemStackKey(stack);
                var remainder = net.getUnifiedStorage().insert(key, amount, false);
                long inserted = amount - remainder.amount();
                
                result.itemTypes++;
                result.itemTotal += amount;
                result.totalResources += amount;
                generated++;
                
            } catch (Exception e) {
                // 跳过有问题的物品
            }
        }
    }
    
    /**
     * 生成流体
     */
    private static void generateFluids(DimensionsNet net, int typeCount, int minAmount, int maxAmount,
                                      Random random, net.minecraft.server.MinecraftServer server, GenerationResult result) {
        // 获取所有流体
        List<Fluid> allFluids = new ArrayList<>(ForgeRegistries.FLUIDS.getValues());
        Collections.shuffle(allFluids, random);
        
        int generated = 0;
        for (Fluid fluid : allFluids) {
            if (generated >= typeCount) break;
            
            // 跳过无效流体
            if (fluid == Fluids.EMPTY || fluid == null) continue;
            
            try {
                // 创建流体堆栈
                FluidStack stack = new FluidStack(fluid, 1000);
                int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                
                // 插入到网络
                FluidStackKey key = new FluidStackKey(stack);
                var remainder = net.getUnifiedStorage().insert(key, amount, false);
                long inserted = amount - remainder.amount();
                
                result.fluidTypes++;
                result.fluidTotal += amount;
                result.totalResources += amount;
                generated++;
                
            } catch (Exception e) {
                // 跳过有问题的流体
            }
        }
    }
    
    /**
     * 生成能量
     */
    private static void generateEnergy(DimensionsNet net, int typeCount, int minAmount, int maxAmount,
                                      Random random, GenerationResult result) {
        // 生成能量（使用默认能量类型）
        for (int i = 0; i < typeCount; i++) {
            try {
                int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                
                // 插入到网络（能量使用默认类型）
                EnergyStackKey key = EnergyStackKey.INSTANCE;
                var remainder = net.getUnifiedStorage().insert(key, amount, false);
                long inserted = amount - remainder.amount();
                
                result.energyTypes++;
                result.energyTotal += amount;
                result.totalResources += amount;
                
            } catch (Exception e) {
                // 跳过有问题的能量
            }
        }
    }
    
    /**
     * 生成所有资源
     */
    private static void generateAllResources(DimensionsNet net, int minAmount, int maxAmount,
                                           boolean withEnchantments, boolean withNbt, Random random,
                                           net.minecraft.server.MinecraftServer server, GenerationResult result) {
        // 生成物品
        generateItems(net, Integer.MAX_VALUE, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
        
        // 生成流体
        generateFluids(net, Integer.MAX_VALUE, minAmount, maxAmount, random, server, result);
        
        // 生成能量
        generateEnergy(net, 1, minAmount, maxAmount, random, result);
    }
    
    /**
     * 添加随机附魔（强制添加，不检查兼容性）
     * @return 成功添加的附魔数量
     */
    private static int addRandomEnchantments(ItemStack stack, Random random) {
        // 获取所有可用的附魔
        List<Enchantment> allEnchantments = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());
        
        if (allEnchantments.isEmpty()) {
            return 0; // 没有可用的附魔
        }
        
        Collections.shuffle(allEnchantments, random);
        
        // 随机选择1-5个附魔（强制添加）
        int maxEnchantCount = Math.min(5, allEnchantments.size());
        int enchantCount = random.nextInt(maxEnchantCount) + 1; // 1-5个附魔
        
        int addedCount = 0;
        // 强制添加附魔，不检查兼容性
        for (int i = 0; i < enchantCount && i < allEnchantments.size(); i++) {
            Enchantment enchantment = allEnchantments.get(i);
            
            // 随机等级（1到最大等级，但可以超过最大等级）
            int maxLevel = enchantment.getMaxLevel();
            // 允许超过最大等级（1-2倍最大等级）
            int levelMultiplier = random.nextInt(2) + 1; // 1-2倍
            int level = random.nextInt(maxLevel * levelMultiplier) + 1;
            
            // 强制添加附魔
            try {
                stack.enchant(enchantment, level);
                addedCount++;
            } catch (Exception e) {
                // 如果添加失败，尝试下一个附魔
                continue;
            }
        }
        
        return addedCount;
    }
    
    /**
     * 添加随机NBT（强制添加大量随机NBT）
     */
    private static void addRandomNbt(ItemStack stack, Random random) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        
        // 强制添加大量随机NBT数据
        tag.putString("generated_by", "bdtools");
        tag.putLong("timestamp", System.currentTimeMillis());
        tag.putInt("random_seed", random.nextInt(1000000));
        tag.putInt("generation_id", random.nextInt(10000));
        
        // 强制添加显示名称
        String[] prefixes = {"Super", "Mega", "Ultra", "Hyper", "Epic", "Legendary", "Mythic", "Divine"};
        String[] suffixes = {"Item", "Tool", "Gear", "Artifact", "Relic", "Treasure", "Wonder"};
        String displayName = prefixes[random.nextInt(prefixes.length)] + " " + 
                           suffixes[random.nextInt(suffixes.length)] + " #" + random.nextInt(9999);
        tag.putString("CustomName", "{\"text\":\"" + displayName + "\",\"color\":\"gold\",\"bold\":true}");
        
        // 强制添加Lore（描述文本）
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        int loreCount = random.nextInt(5) + 2; // 2-6行
        String[] loreTexts = {
            "Generated by BD Tools",
            "Power Level: " + (random.nextInt(100) + 1),
            "Rarity: " + (random.nextInt(10) + 1) + "/10",
            "Magic: " + random.nextInt(1000),
            "Durability: " + random.nextInt(100) + "%",
            "Special Attribute: " + random.nextInt(50),
            "Enchantment Power: " + (random.nextInt(100) + 50)
        };
        
        for (int i = 0; i < loreCount; i++) {
            String loreText = loreTexts[random.nextInt(loreTexts.length)];
            lore.add(net.minecraft.nbt.StringTag.valueOf("{\"text\":\"" + loreText + "\",\"color\":\"gray\",\"italic\":true}"));
        }
        
        net.minecraft.nbt.CompoundTag display = new net.minecraft.nbt.CompoundTag();
        display.put("Lore", lore);
        tag.put("display", display);
        
        // 强制添加大量自定义数据
        tag.putInt("custom_int_1", random.nextInt(10000));
        tag.putInt("custom_int_2", random.nextInt(10000));
        tag.putInt("custom_int_3", random.nextInt(10000));
        tag.putDouble("custom_double_1", random.nextDouble() * 100);
        tag.putDouble("custom_double_2", random.nextDouble() * 1000);
        tag.putBoolean("custom_bool_1", random.nextBoolean());
        tag.putBoolean("custom_bool_2", random.nextBoolean());
        tag.putBoolean("custom_bool_3", random.nextBoolean());
        tag.putString("custom_string_1", "random_" + random.nextInt(10000));
        tag.putString("custom_string_2", "value_" + random.nextInt(10000));
        
        // 强制添加嵌套NBT
        net.minecraft.nbt.CompoundTag nested1 = new net.minecraft.nbt.CompoundTag();
        nested1.putString("nested_type", "type_" + random.nextInt(10));
        nested1.putInt("nested_value", random.nextInt(1000));
        nested1.putBoolean("nested_flag", random.nextBoolean());
        tag.put("nested_data_1", nested1);
        
        net.minecraft.nbt.CompoundTag nested2 = new net.minecraft.nbt.CompoundTag();
        nested2.putInt("data_a", random.nextInt(500));
        nested2.putInt("data_b", random.nextInt(500));
        nested2.putInt("data_c", random.nextInt(500));
        tag.put("nested_data_2", nested2);
        
        // 强制添加列表NBT
        net.minecraft.nbt.ListTag randomList = new net.minecraft.nbt.ListTag();
        int listSize = random.nextInt(10) + 5; // 5-14个元素
        for (int i = 0; i < listSize; i++) {
            net.minecraft.nbt.CompoundTag listItem = new net.minecraft.nbt.CompoundTag();
            listItem.putInt("id", i);
            listItem.putInt("value", random.nextInt(1000));
            listItem.putString("name", "item_" + i);
            randomList.add(listItem);
        }
        tag.put("random_data_list", randomList);
        
        // 强制添加耐久度数据（随机损坏）
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            // 随机损坏0-90%
            int damagePercent = random.nextInt(91);
            int currentDamage = (maxDamage * damagePercent) / 100;
            tag.putInt("Damage", currentDamage);
        }
        
        // 强制添加隐藏标签
        tag.putBoolean("is_generated", true);
        tag.putBoolean("has_special_nbt", true);
        tag.putInt("generation_version", 2);
        tag.putString("generator", "bdtools_force_enchant");
        
        // 添加更多随机NBT
        for (int i = 0; i < 3; i++) {
            String key = "extra_data_" + (i + 1);
            switch (random.nextInt(4)) {
                case 0:
                    tag.putInt(key, random.nextInt(10000));
                    break;
                case 1:
                    tag.putDouble(key, random.nextDouble() * 1000);
                    break;
                case 2:
                    tag.putBoolean(key, random.nextBoolean());
                    break;
                case 3:
                    tag.putString(key, "extra_value_" + random.nextInt(1000));
                    break;
            }
        }
    }
    
    /**
     * 发送生成结果
     */
    private static void sendGenerationResult(CommandSourceStack source, int netId, 
                                           GenerationResult result, String resourceType) {
        // 添加标题
        String resourceTypeDisplay = getResourceTypeDisplay(resourceType);
        MutableComponent message = OutputFormatter.createTitle("network.generate.result.title", netId, resourceTypeDisplay)
                .append(Component.literal("\n"));
        
        // 添加物品统计
        if (result.itemTypes > 0) {
            message = message.append(Component.literal(CommandLang.get("network.generate.result.items", 
                    result.itemTypes, result.itemTotal))
                    .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("\n"));
        }
        
        // 添加流体统计
        if (result.fluidTypes > 0) {
            message = message.append(Component.literal(CommandLang.get("network.generate.result.fluids", 
                    result.fluidTypes, result.fluidTotal))
                    .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n"));
        }
        
        // 添加能量统计
        if (result.energyTypes > 0) {
            message = message.append(Component.literal(CommandLang.get("network.generate.result.energy", 
                    result.energyTypes, result.energyTotal))
                    .withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("\n"));
        }
        
        // 添加附魔统计（如果生成了附魔）
        if (result.itemsWithEnchantments > 0) {
            message = message.append(Component.literal(CommandLang.get("network.generate.result.enchantments", 
                    result.itemsWithEnchantments, result.totalEnchantments))
                    .withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("\n"));
        }
        
        // 添加NBT统计（如果生成了NBT）
        if (result.itemsWithNbt > 0) {
            String formattedSize = formatFileSize(result.estimatedNbtSize);
            message = message.append(Component.literal(CommandLang.get("network.generate.result.nbt_size", 
                    result.itemsWithNbt, formattedSize))
                    .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"));
            
            // 添加NBT警告（如果NBT数据较大）
            if (result.estimatedNbtSize > 10240) { // 大于10KB
                message = message.append(Component.literal(CommandLang.get("network.generate.result.nbt_warning"))
                        .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n"));
            }
        }
        
        // 添加总计
        int totalTypes = result.itemTypes + result.fluidTypes + result.energyTypes;
        MutableComponent finalMessage = message.append(Component.literal(CommandLang.get("network.generateResources.detailed_total", 
                totalTypes, result.totalResources))
                .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));
        
        source.sendSuccess(() -> finalMessage, false);
    }
    
    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * 获取资源类型显示文本
     */
    private static String getResourceTypeDisplay(String resourceType) {
        switch (resourceType.toLowerCase()) {
            case "items":
                return CommandLang.get("network.generateItems.resource_type.items");
            case "fluids":
                return CommandLang.get("network.generateItems.resource_type.fluids");
            case "energy":
                return CommandLang.get("network.generateItems.resource_type.energy");
            case "mixed":
                return CommandLang.get("network.generateItems.resource_type.mixed");
            case "all":
                return CommandLang.get("network.generateItems.resource_type.all");
            default:
                return resourceType;
        }
    }
    
    /**
     * 生成结果类
     */
    private static class GenerationResult {
        int itemTypes = 0;
        long itemTotal = 0;
        int fluidTypes = 0;
        long fluidTotal = 0;
        int energyTypes = 0;
        long energyTotal = 0;
        long totalResources = 0;
        
        // NBT相关统计
        int itemsWithNbt = 0;
        long estimatedNbtSize = 0; // 字节
        
        // 附魔相关统计
        int itemsWithEnchantments = 0;
        int totalEnchantments = 0;
    }
}