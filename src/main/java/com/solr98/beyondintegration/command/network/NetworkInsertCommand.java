package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络插入命令
 * 功能：向网络插入物品、流体或能量
 */
public class NetworkInsertCommand {
    
    /**
     * 注册命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register(net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("insert")
            // 物品插入命令
            .then(buildItemInsertCommand(context))
            // 物品标签批量插入命令
            .then(buildTagInsertCommand(context))
            // 药水护符批量插入命令
            .then(buildPotionCharmInsertCommand(context))
            // 流体插入命令
            .then(buildFluidInsertCommand(context))
            // 能量插入命令（键来自 BD 注册表）
            .then(buildEnergyInsertCommand());
    }

    /**
     * 构建药水护符批量插入命令（需加载神化 Apotheosis）。
     * <p>用法：{@code insert potionCharm <potion|all> [count] [plain|mending|unbreakable] [netId]}。
     * 默认：数量 1、plain、当前玩家主网络。生成的护符默认 {@code charm_enabled=true}。
     * {@code all} 表示对全部"合法药水"（单条非即时效果）各插入一批。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildPotionCharmInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("potionCharm")
            .then(Commands.argument("potion", ResourceArgument.resource(context, Registries.POTION))
                .executes(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx), 1, "plain", -1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                            IntegerArgumentType.getInteger(ctx, "count"), "plain", -1))
                    .then(Commands.literal("plain")
                        .executes(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                                IntegerArgumentType.getInteger(ctx, "count"), "plain", -1))
                        .then(buildNetIdArg(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                                IntegerArgumentType.getInteger(ctx, "count"), "plain",
                                IntegerArgumentType.getInteger(ctx, "netId")))))
                    .then(Commands.literal("mending")
                        .executes(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                                IntegerArgumentType.getInteger(ctx, "count"), "mending", -1))
                        .then(buildNetIdArg(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                                IntegerArgumentType.getInteger(ctx, "count"), "mending",
                                IntegerArgumentType.getInteger(ctx, "netId")))))
                    .then(Commands.literal("unbreakable")
                        .executes(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                                IntegerArgumentType.getInteger(ctx, "count"), "unbreakable", -1))
                        .then(buildNetIdArg(ctx -> executeInsertPotionCharm(ctx, getPotion(ctx),
                                IntegerArgumentType.getInteger(ctx, "count"), "unbreakable",
                                IntegerArgumentType.getInteger(ctx, "netId")))))))
            // all / positive / negative：对相应合法药水各插入一批（按效果类别区分正面/负面）
            .then(buildPotionCharmAllBranch("all", "all"))
            .then(buildPotionCharmAllBranch("positive", "positive"))
            .then(buildPotionCharmAllBranch("negative", "negative"));
    }

    /** 构建 all/positive/negative 分支（同一套 count → plain|mending|unbreakable → netId 结构）。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildPotionCharmAllBranch(
            String literal, String category) {
        return Commands.literal(literal)
            .executes(ctx -> executeInsertAllPotionCharms(ctx, 1, "plain", -1, category))
            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                .executes(ctx -> executeInsertAllPotionCharms(ctx,
                        IntegerArgumentType.getInteger(ctx, "count"), "plain", -1, category))
                .then(Commands.literal("plain")
                    .executes(ctx -> executeInsertAllPotionCharms(ctx,
                            IntegerArgumentType.getInteger(ctx, "count"), "plain", -1, category))
                    .then(buildNetIdArg(ctx -> executeInsertAllPotionCharms(ctx,
                            IntegerArgumentType.getInteger(ctx, "count"), "plain",
                            IntegerArgumentType.getInteger(ctx, "netId"), category))))
                .then(Commands.literal("mending")
                    .executes(ctx -> executeInsertAllPotionCharms(ctx,
                            IntegerArgumentType.getInteger(ctx, "count"), "mending", -1, category))
                    .then(buildNetIdArg(ctx -> executeInsertAllPotionCharms(ctx,
                            IntegerArgumentType.getInteger(ctx, "count"), "mending",
                            IntegerArgumentType.getInteger(ctx, "netId"), category))))
                .then(Commands.literal("unbreakable")
                    .executes(ctx -> executeInsertAllPotionCharms(ctx,
                            IntegerArgumentType.getInteger(ctx, "count"), "unbreakable", -1, category))
                    .then(buildNetIdArg(ctx -> executeInsertAllPotionCharms(ctx,
                            IntegerArgumentType.getInteger(ctx, "count"), "unbreakable",
                            IntegerArgumentType.getInteger(ctx, "netId"), category)))));
    }

    /** 可选 netId 参数（0..9999） */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> buildNetIdArg(
            com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.argument("netId", IntegerArgumentType.integer(0, 9999)).executes(command);
    }

    /** 从命令上下文解析药水 */
    private static Potion getPotion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "potion", Registries.POTION).value();
    }

    /** 执行药水护符批量插入 */
    private static int executeInsertPotionCharm(CommandContext<CommandSourceStack> ctx, Potion potion,
                                                int count, String mode, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;

        // 解析目标网络：netId<0 时使用执行者主网络
        DimensionsNet net;
        int actualNetId;
        if (netId < 0) {
            ServerPlayer executor = source.getPlayer();
            if (executor == null) {
                source.sendFailure(OutputFormatter.createError("error.player_required"));
                return 0;
            }
            net = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (net == null) {
                source.sendFailure(OutputFormatter.createError("error.not_in_network"));
                return 0;
            }
            actualNetId = net.getId();
        } else {
            net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) return 0;
            actualNetId = netId;
        }

        // 神化药水护符物品
        Item charmItem = BuiltInRegistries.ITEM.get(new ResourceLocation("apotheosis", "potion_charm"));
        if (charmItem == Items.AIR) {
            source.sendFailure(OutputFormatter.createError("error.apotheosis_required"));
            return 0;
        }

        ItemStack prototype = buildPotionCharmStack(charmItem, potion, mode);
        ItemStackKey key = new ItemStackKey(prototype.copyWithCount(1));
        if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
        long inserted = count - remaining.amount();
        if (inserted <= 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        net.setDirty();

        String potionName = BuiltInRegistries.POTION.getKey(potion).toString();
        final int finalNetId = actualNetId;
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.potionCharm.success", inserted, potionName, mode, finalNetId)
        ), false);
        return 1;
    }

    /** 构造带指定药水与模式的药水护符（charm_enabled=true；mending 写入 Mending 附魔；unbreakable 写入 Unbreakable） */
    private static ItemStack buildPotionCharmStack(Item charmItem, Potion potion, String mode) {
        ItemStack stack = new ItemStack(charmItem);
        PotionUtils.setPotion(stack, potion);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("charm_enabled", true);
        if ("unbreakable".equals(mode)) {
            tag.putBoolean("Unbreakable", true);
        } else if ("mending".equals(mode)) {
            CompoundTag ench = new CompoundTag();
            ench.putString("id", BuiltInRegistries.ENCHANTMENT.getKey(Enchantments.MENDING).toString());
            ench.putShort("lvl", (short) 1);
            ListTag list = new ListTag();
            list.add(ench);
            tag.put("Enchantments", list);
        }
        return stack;
    }

    /** 执行"批量插入合法药水护符"：按类别（all/positive/negative）筛选后，每个药水各插入 count 个；空间不足的种类跳过。 */
    private static int executeInsertAllPotionCharms(CommandContext<CommandSourceStack> ctx, int count, String mode, int netId,
                                                    String category) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;

        DimensionsNet net;
        int actualNetId;
        if (netId < 0) {
            ServerPlayer executor = source.getPlayer();
            if (executor == null) {
                source.sendFailure(OutputFormatter.createError("error.player_required"));
                return 0;
            }
            net = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (net == null) {
                source.sendFailure(OutputFormatter.createError("error.not_in_network"));
                return 0;
            }
            actualNetId = net.getId();
        } else {
            net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) return 0;
            actualNetId = netId;
        }

        Item charmItem = BuiltInRegistries.ITEM.get(new ResourceLocation("apotheosis", "potion_charm"));
        if (charmItem == Items.AIR) {
            source.sendFailure(OutputFormatter.createError("error.apotheosis_required"));
            return 0;
        }

        int types = 0;
        long total = 0;
        for (Potion potion : BuiltInRegistries.POTION.stream().toList()) {
            if (!isValidCharmPotion(potion) || !matchesCategory(potion, category)) continue;
            ItemStackKey key = new ItemStackKey(buildPotionCharmStack(charmItem, potion, mode).copyWithCount(1));
            if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) continue;
            KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
            long inserted = count - remaining.amount();
            if (inserted > 0) {
                types++;
                total += inserted;
            }
        }
        if (total <= 0) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        net.setDirty();

        final int fTypes = types;
        final long fTotal = total;
        final int fNetId = actualNetId;
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.potionCharm.all.success", fTypes, fTotal, mode, fNetId,
                    CommandLang.get("network.insert.potionCharm.category." + category))
        ), false);
        return 1;
    }

    /** 是否为可做成护符的"合法药水"：恰好一条非即时效果（与神化 PotionCharmItem.isValidPotion 核心一致）。 */
    private static boolean isValidCharmPotion(Potion potion) {
        var effects = potion.getEffects();
        return effects.size() == 1 && !effects.get(0).getEffect().isInstantenous();
    }

    /** 按效果类别筛选：all=全部；positive=正面(BENEFICIAL)；negative=负面(HARMFUL)；NEUTRAL 仅归入 all。 */
    private static boolean matchesCategory(Potion potion, String category) {
        if (category == null || "all".equals(category)) return true;
        var effectCategory = potion.getEffects().get(0).getEffect().getCategory();
        if ("positive".equals(category)) return effectCategory == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL;
        if ("negative".equals(category)) return effectCategory == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
        return true;
    }

    /**
     * 构建物品插入命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildItemInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("item")
            // 默认：插入到当前玩家的主要网络，数量为1
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.item_required"));
                return 0;
            })
            // 指定物品，默认网络和数量
            .then(Commands.argument("item", ItemArgument.item(context))
                .executes(ctx -> executeInsertItemDefault(ctx, 
                        ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                // 指定物品和数量，默认网络
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertItemDefault(ctx,
                            ItemArgument.getItem(ctx, "item").createItemStack(1, false),
                            LongArgumentType.getLong(ctx, "count")))
                    // 指定网络、物品和数量
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                ItemArgument.getItem(ctx, "item").createItemStack(1, false),
                                LongArgumentType.getLong(ctx, "count"))))))
            // 指定网络，默认物品和数量（需要物品参数）
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.item_required"));
                    return 0;
                })
                .then(Commands.argument("item", ItemArgument.item(context))
                    .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                            ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                    .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                ItemArgument.getItem(ctx, "item").createItemStack(1, false),
                                LongArgumentType.getLong(ctx, "count"))))));
    }
    
    /**
     * 构建物品标签批量插入命令
     * 用法：insert tag <#标签> [每种数量] [netId] 或 insert tag <netId> <#标签> [每种数量]
     * 标签内每种物品各插入 count 个，空间不足的种类自动跳过
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTagInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("tag")
            // 缺少标签参数
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.tag_required"));
                return 0;
            })
            // 指定标签，默认网络和数量（每种1个）
            .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"), 1))
                // 指定标签和数量，默认网络
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"),
                            LongArgumentType.getLong(ctx, "count")))
                    // 指定网络、标签和数量
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))))
            // 指定网络，默认标签和数量
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.tag_required"));
                    return 0;
                })
                .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                    .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                            getItemTag(ctx, "tag"), 1))
                    .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))));
    }

    /** 从命令上下文解析物品标签；输入为单个物品 ID（无 # 前缀）时提示需要标签 */
    private static TagKey<Item> getItemTag(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        var result = ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, name, Registries.ITEM,
                new DynamicCommandExceptionType(arg -> CommandLang.component("error.tag_required")));
        if (result.unwrap().right().isEmpty()) {
            throw new SimpleCommandExceptionType(CommandLang.component("error.tag_required")).create();
        }
        return result.unwrap().right().get();
    }

    /**
     * 构建流体插入命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildFluidInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("fluid")
            // 默认：插入到当前玩家的主要网络，数量为1000mB
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.fluid_required"));
                return 0;
            })
            // 指定流体，默认网络和数量（1000mB）
            .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                .executes(ctx -> {
                    var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                    return executeInsertFluidDefault(ctx, fluidHolder.value(), 1000L);
                })
                // 指定流体和数量，默认网络
                .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> {
                        var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                        return executeInsertFluidDefault(ctx, fluidHolder.value(), 
                                LongArgumentType.getLong(ctx, "amount"));
                    })
                    // 指定网络、流体和数量
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> {
                            var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                            return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                    fluidHolder.value(), LongArgumentType.getLong(ctx, "amount"));
                        }))))
            // 指定网络，默认流体和数量（需要流体参数）
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.fluid_required"));
                    return 0;
                })
                .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                    .executes(ctx -> {
                        var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                        return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                fluidHolder.value(), 1000L);
                    })
                    .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> {
                            var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                            return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                    fluidHolder.value(), LongArgumentType.getLong(ctx, "amount"));
                        }))));
    }
    
    /**
     * 构建能量插入命令（仅能量键形态）。
     * 用法：{@code energy <注册键> [数量] [netId]}
     * <p>注册键取自 Beyond Dimensions 注册表（{@link StackKeyRegistry}），形如 {@code beyonddimensions:stack_type/energy}；
     * 以注册表中登记的存储键实例插入。省略数量为 1000、网络为执行者主网络。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildEnergyInsertCommand() {
        return Commands.literal("energy")
            .then(Commands.argument("key", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> suggestEnergyKeys(builder))
                .executes(ctx -> executeInsertEnergyKey(ctx,
                        ResourceLocationArgument.getId(ctx, "key"), 1000L, -1))
                .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertEnergyKey(ctx,
                            ResourceLocationArgument.getId(ctx, "key"),
                            LongArgumentType.getLong(ctx, "amount"), -1))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertEnergyKey(ctx,
                                ResourceLocationArgument.getId(ctx, "key"),
                                LongArgumentType.getLong(ctx, "amount"),
                                IntegerArgumentType.getInteger(ctx, "netId"))))));
    }

    /**
     * 能量键补全：显示为对管理人员友好的 {@code 模组id:键名}（去掉内部的 {@code stack_type/} 前缀），
     * tooltip 给出注册表中的完整键 ID。输入同时兼容短名与完整 ID。
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestEnergyKeys(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (IStackKey<?> type : StackKeyRegistry.getAllTypes()) {
            // 仅限能量类键（LongStackKey 派生：能量 / 闪电 / 灵魂 / 魔源 / 魔力），排除物品 / 流体 / 化学品等
            if (!(type instanceof LongStackKey)) continue;
            ResourceLocation id = type.getTypeId();
            String shortId = shortKeyId(id);
            if (shortId.toLowerCase().startsWith(remaining) || id.toString().toLowerCase().startsWith(remaining)) {
                builder.suggest(shortId, Component.literal(id.toString()));
            }
        }
        return builder.buildFuture();
    }

    /** 短键名：{@code namespace:path}，去掉 path 中的 {@code stack_type/} 前缀。 */
    private static String shortKeyId(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("stack_type/")) {
            path = path.substring("stack_type/".length());
        }
        return id.getNamespace() + ":" + path;
    }

    /** 解析键 ID：先按原样查表；失败则补 {@code stack_type/} 前缀再查（兼容短名）。 */
    private static IStackKey<?> resolveKeyId(ResourceLocation id) {
        try {
            return StackKeyRegistry.getType(id);
        } catch (IllegalArgumentException first) {
            ResourceLocation full = ResourceLocation.tryBuild(id.getNamespace(), "stack_type/" + id.getPath());
            if (full != null) {
                try {
                    return StackKeyRegistry.getType(full);
                } catch (IllegalArgumentException ignored) {
                }
            }
            throw first;
        }
    }
    
    // ========== 物品插入方法 ==========
    
    /**
     * 执行插入物品到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertItemDefault(CommandContext<CommandSourceStack> ctx, 
                                               ItemStack itemStack, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        int netId = primaryNet.getId();
        return executeInsertItemInternal(source, primaryNet, netId, itemStack, count);
    }
    
    /**
     * 执行插入物品命令
     */
    private static int executeInsertItem(CommandContext<CommandSourceStack> ctx, int netId, 
                                        ItemStack itemStack, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertItemInternal(source, net, netId, itemStack, count);
    }
    
    /**
     * 内部：执行物品插入
     */
    private static int executeInsertItemInternal(CommandSourceStack source, DimensionsNet net, 
                                                int netId, ItemStack itemStack, long count) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, count)) {
            return 0;
        }
        
        // 创建物品键
        ItemStackKey itemKey = new ItemStackKey(itemStack.copyWithCount(1));
        
        // 检查存储空间
        if (!NetworkUtils.hasEnoughStorageForItem(net, itemKey, count)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 插入物品
        KeyAmount remaining = net.getUnifiedStorage().insert(itemKey, count, false);
        
        if (remaining.amount() > 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 发送成功消息
        String itemName = itemStack.getHoverName().getString();
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.item.success", count, itemName, netId)
        ), false);
        
        return 1;
    }
    
    // ========== 物品标签插入方法 ==========
    
    /**
     * 执行插入标签物品到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertTagDefault(CommandContext<CommandSourceStack> ctx,
                                               TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        return executeInsertTagInternal(source, primaryNet, primaryNet.getId(), tag, count);
    }
    
    /**
     * 执行插入标签物品命令
     */
    private static int executeInsertTag(CommandContext<CommandSourceStack> ctx, int netId,
                                        TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertTagInternal(source, net, netId, tag, count);
    }
    
    /**
     * 内部：按物品标签批量插入，结果以列表逐种输出。
     * 标签内每种物品各插入 count 个；单种空间不足时跳过该种（不影响其他种类）。
     * 输出：标题行 + 每种物品一行（成功显示物品与数量，跳过显示原因）+ 汇总行。
     */
    private static int executeInsertTagInternal(CommandSourceStack source, DimensionsNet net,
                                                int netId, TagKey<Item> tag, long count) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, count)) {
            return 0;
        }
        
        // 收集标签内全部物品
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            items.add(holder.value());
        }
        if (items.isEmpty()) {
            source.sendFailure(OutputFormatter.createError("error.tag_empty", tag.location().toString()));
            return 0;
        }
        
        String tagName = "#" + tag.location();
        int insertedTypes = 0;
        int skippedTypes = 0;
        long totalInserted = 0;
        
        // 标题行
        source.sendSuccess(() -> OutputFormatter.createTitle("network.insert.tag.title", netId, tagName), false);
        
        // 逐种物品插入并输出列表行
        for (Item item : items) {
            ItemStack stack = new ItemStack(item);
            ItemStackKey key = new ItemStackKey(stack.copyWithCount(1));
            
            // 空间不足的种类跳过，不影响其他种类
            if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) {
                skippedTypes++;
                source.sendSuccess(() -> OutputFormatter.createWarning("network.insert.tag.line.skipped",
                        stack.getHoverName().getString()), false);
                continue;
            }
            
            KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
            long inserted = count - remaining.amount();
            if (inserted > 0) {
                insertedTypes++;
                totalInserted += inserted;
            }
            if (remaining.amount() > 0) {
                skippedTypes++;
            }
            
            // 成功行（物品名 x实际插入数量）；未插入任何数量时按跳过提示
            final long shown = inserted;
            if (inserted > 0) {
                source.sendSuccess(() -> OutputFormatter.createItemDisplay(stack, shown), false);
            } else {
                source.sendSuccess(() -> OutputFormatter.createWarning("network.insert.tag.line.skipped",
                        stack.getHoverName().getString()), false);
            }
        }
        
        if (totalInserted <= 0) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 汇总行
        final int okTypes = insertedTypes;
        final int skipTypes = skippedTypes;
        final long sumInserted = totalInserted;
        source.sendSuccess(() -> OutputFormatter.createSuccess("network.insert.tag.summary",
                okTypes, items.size(), count, sumInserted, skipTypes), false);
        
        return 1;
    }
    
    // ========== 流体插入方法 ==========
    
    /**
     * 执行插入流体到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertFluidDefault(CommandContext<CommandSourceStack> ctx, 
                                                Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        int netId = primaryNet.getId();
        return executeInsertFluidInternal(source, primaryNet, netId, fluid, amount);
    }
    
    /**
     * 执行插入流体命令
     */
    private static int executeInsertFluid(CommandContext<CommandSourceStack> ctx, int netId, 
                                         Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertFluidInternal(source, net, netId, fluid, amount);
    }
    
    /**
     * 内部：执行流体插入
     */
    private static int executeInsertFluidInternal(CommandSourceStack source, DimensionsNet net, 
                                                 int netId, Fluid fluid, long amount) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, amount)) {
            return 0;
        }
        
        // 创建流体堆栈和键
        FluidStack fluidStack = new FluidStack(fluid, 1);
        FluidStackKey fluidKey = new FluidStackKey(fluidStack);
        
        // 检查存储空间
        if (!NetworkUtils.hasEnoughStorageForFluid(net, fluidKey, amount)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 插入流体
        KeyAmount remaining = net.getUnifiedStorage().insert(fluidKey, amount, false);
        
        if (remaining.amount() > 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 发送成功消息
        String fluidName = fluid.getFluidType().getDescription().getString();
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.fluid.success", amount, fluidName, netId)
        ), false);
        
        return 1;
    }
    
    // ========== 能量插入方法 ==========
    
    /**
     * 执行"能量键插入"：从 BD 注册表按类型 ID 解析存储键并插入指定数量。
     * netId&lt;0 时使用执行者主网络；键未注册时提示错误。
     */
    private static int executeInsertEnergyKey(CommandContext<CommandSourceStack> ctx,
                                              ResourceLocation keyId, long amount, int netId) {
        CommandSourceStack source = ctx.getSource();

        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }

        // 解析目标网络
        DimensionsNet net;
        int actualNetId;
        if (netId < 0) {
            ServerPlayer executor = source.getPlayer();
            if (executor == null) {
                source.sendFailure(OutputFormatter.createError("error.player_required"));
                return 0;
            }
            net = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (net == null) {
                source.sendFailure(OutputFormatter.createError("error.not_in_network"));
                return 0;
            }
            actualNetId = net.getId();
        } else {
            net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) return 0;
            actualNetId = netId;
        }

        // 从 Beyond Dimensions 注册表解析存储键（未注册 -> 报错）
        IStackKey<?> key;
        try {
            key = resolveKeyId(keyId);
        } catch (IllegalArgumentException e) {
            source.sendFailure(OutputFormatter.createError("error.key_not_registered", keyId.toString()));
            return 0;
        }
        // 仅限能量类键（LongStackKey 派生）
        if (!(key instanceof LongStackKey)) {
            source.sendFailure(OutputFormatter.createError("error.energy_key_required"));
            return 0;
        }

        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, amount)) {
            return 0;
        }

        // 检查存储空间
        if (!NetworkUtils.hasEnoughStorageForKey(net, key, amount)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }

        // 插入
        KeyAmount remaining = net.getUnifiedStorage().insert(key, amount, false);
        if (remaining.amount() > 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }

        // 标记网络为脏数据
        net.setDirty();

        final int fNetId = actualNetId;
        final String keyName = key.getTypeId().toString();
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.energy.key.success", amount, keyName, fNetId)
        ), false);

        return 1;
    }
}
