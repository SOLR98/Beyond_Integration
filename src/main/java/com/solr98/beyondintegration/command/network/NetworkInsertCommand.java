package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.solr98.beyondintegration.core.util.ItemNbt;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class NetworkInsertCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register(CommandBuildContext context) {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("insert")
                .then(buildItemInsertCommand(context))
                .then(buildTagInsertCommand(context))
                .then(buildFluidInsertCommand(context))
                .then(buildEnergyInsertCommand());
        // 神化（Apotheosis）分支：药水护符（仅在神化加载时注册）
        if (net.neoforged.fml.ModList.get().isLoaded("apotheosis")) {
            builder.then(buildPotionCharmInsertCommand(context));
        }
        // TACZ 分支：弹药 / 配件（仅在 TACZ 加载时注册）
        if (net.neoforged.fml.ModList.get().isLoaded("tacz")) {
            builder.then(buildTaczAmmoInsertCommand());
            builder.then(buildTaczAttachmentInsertCommand());
        }
        return builder;
    }

    // ========== TACZ 弹药 / 配件插入（仅 TACZ 加载时注册） ==========

    /** 用法：{@code insert taczAmmo <弹药ID> [count] [netId]} / {@code insert taczAmmo all [count] [netId]} */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTaczAmmoInsertCommand() {
        return Commands.literal("taczAmmo")
            .then(Commands.literal("all")
                .executes(ctx -> executeInsertAllTaczAmmoDefault(ctx, 1))
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertAllTaczAmmoDefault(ctx,
                            LongArgumentType.getLong(ctx, "count")))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertAllTaczAmmo(ctx,
                                IntegerArgumentType.getInteger(ctx, "netId"),
                                LongArgumentType.getLong(ctx, "count"))))))
            .then(Commands.argument("ammoId", ResourceLocationArgument.id())
                .executes(ctx -> executeInsertTaczAmmoDefault(ctx,
                        ResourceLocationArgument.getId(ctx, "ammoId"), 1))
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertTaczAmmoDefault(ctx,
                            ResourceLocationArgument.getId(ctx, "ammoId"),
                            LongArgumentType.getLong(ctx, "count")))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertTaczAmmo(ctx,
                                IntegerArgumentType.getInteger(ctx, "netId"),
                                ResourceLocationArgument.getId(ctx, "ammoId"),
                                LongArgumentType.getLong(ctx, "count"))))));
    }

    /** 用法：{@code insert taczAttachment <配件ID> [count] [netId]} / {@code insert taczAttachment all [count] [netId]} */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTaczAttachmentInsertCommand() {
        return Commands.literal("taczAttachment")
            .then(Commands.literal("all")
                .executes(ctx -> executeInsertAllTaczAttachmentDefault(ctx, 1))
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertAllTaczAttachmentDefault(ctx,
                            LongArgumentType.getLong(ctx, "count")))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertAllTaczAttachment(ctx,
                                IntegerArgumentType.getInteger(ctx, "netId"),
                                LongArgumentType.getLong(ctx, "count"))))))
            .then(Commands.argument("attachmentId", ResourceLocationArgument.id())
                .executes(ctx -> executeInsertTaczAttachmentDefault(ctx,
                        ResourceLocationArgument.getId(ctx, "attachmentId"), 1))
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertTaczAttachmentDefault(ctx,
                            ResourceLocationArgument.getId(ctx, "attachmentId"),
                            LongArgumentType.getLong(ctx, "count")))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertTaczAttachment(ctx,
                                IntegerArgumentType.getInteger(ctx, "netId"),
                                ResourceLocationArgument.getId(ctx, "attachmentId"),
                                LongArgumentType.getLong(ctx, "count"))))));
    }

    private static int executeInsertTaczAmmoDefault(CommandContext<CommandSourceStack> ctx,
                                                    ResourceLocation ammoId, long count) {
        ItemStack stack = buildTaczAmmoStack(ammoId);
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(OutputFormatter.createError("error.tacz_ammo_unknown", ammoId.toString()));
            return 0;
        }
        return executeInsertItemDefault(ctx, stack, count);
    }

    private static int executeInsertTaczAmmo(CommandContext<CommandSourceStack> ctx, int netId,
                                             ResourceLocation ammoId, long count) {
        ItemStack stack = buildTaczAmmoStack(ammoId);
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(OutputFormatter.createError("error.tacz_ammo_unknown", ammoId.toString()));
            return 0;
        }
        return executeInsertItem(ctx, netId, stack, count);
    }

    private static int executeInsertTaczAttachmentDefault(CommandContext<CommandSourceStack> ctx,
                                                          ResourceLocation attachmentId, long count) {
        ItemStack stack = buildTaczAttachmentStack(attachmentId);
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(OutputFormatter.createError(
                    "error.tacz_attachment_unknown", attachmentId.toString()));
            return 0;
        }
        return executeInsertItemDefault(ctx, stack, count);
    }

    private static int executeInsertTaczAttachment(CommandContext<CommandSourceStack> ctx, int netId,
                                                   ResourceLocation attachmentId, long count) {
        ItemStack stack = buildTaczAttachmentStack(attachmentId);
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(OutputFormatter.createError(
                    "error.tacz_attachment_unknown", attachmentId.toString()));
            return 0;
        }
        return executeInsertItem(ctx, netId, stack, count);
    }

    /** 按弹药 ID 构造 {@code tacz:ammo} 物品（ID 不在索引中则返回空）。 */
    private static ItemStack buildTaczAmmoStack(ResourceLocation ammoId) {
        if (com.tacz.guns.api.TimelessAPI.getCommonAmmoIndex(ammoId).isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.AMMO.get());
        if (stack.getItem() instanceof com.tacz.guns.api.item.IAmmo iAmmo) {
            iAmmo.setAmmoId(stack, ammoId);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 按配件 ID 构造 {@code tacz:attachment} 物品（ID 不在索引中则返回空）。 */
    private static ItemStack buildTaczAttachmentStack(ResourceLocation attachmentId) {
        if (com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(attachmentId).isEmpty()) {
            return ItemStack.EMPTY;
        }
        return com.tacz.guns.api.item.builder.AttachmentItemBuilder.create()
                .setId(attachmentId)
                .build();
    }

    private static int executeInsertAllTaczAmmoDefault(CommandContext<CommandSourceStack> ctx, long count) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (net == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        return executeInsertAllTaczAmmoInternal(source, net, net.getId(), count);
    }

    private static int executeInsertAllTaczAmmo(CommandContext<CommandSourceStack> ctx, int netId, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertAllTaczAmmoInternal(source, net, netId, count);
    }

    private static int executeInsertAllTaczAmmoInternal(CommandSourceStack source, DimensionsNet net,
                                                       int netId, long count) {
        int inserted = 0;
        int failed = 0;
        for (var entry : com.tacz.guns.api.TimelessAPI.getAllCommonAmmoIndex()) {
            ItemStack stack = buildTaczAmmoStack(entry.getKey());
            if (stack.isEmpty()) continue;
            KeyAmount remaining = net.getUnifiedStorage().insert(
                    new ItemStackKey(stack.copyWithCount(1)), count, false);
            if (remaining.amount() > 0L) failed++;
            else inserted++;
        }
        net.setDirty();
        int finalInserted = inserted;
        int finalFailed = failed;
        source.sendSuccess(() -> Component.literal(
                "TACZ 弹药插入完成：成功 " + finalInserted + " 种，失败 " + finalFailed + " 种"
                        + "（每种 " + count + " 个，netId=" + netId + "）"), false);
        return inserted > 0 ? 1 : 0;
    }

    private static int executeInsertAllTaczAttachmentDefault(CommandContext<CommandSourceStack> ctx, long count) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (net == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        return executeInsertAllTaczAttachmentInternal(source, net, net.getId(), count);
    }

    private static int executeInsertAllTaczAttachment(CommandContext<CommandSourceStack> ctx, int netId, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertAllTaczAttachmentInternal(source, net, netId, count);
    }

    private static int executeInsertAllTaczAttachmentInternal(CommandSourceStack source, DimensionsNet net,
                                                             int netId, long count) {
        int inserted = 0;
        int failed = 0;
        for (var entry : com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex()) {
            ItemStack stack = buildTaczAttachmentStack(entry.getKey());
            if (stack.isEmpty()) continue;
            KeyAmount remaining = net.getUnifiedStorage().insert(
                    new ItemStackKey(stack.copyWithCount(1)), count, false);
            if (remaining.amount() > 0L) failed++;
            else inserted++;
        }
        net.setDirty();
        int finalInserted = inserted;
        int finalFailed = failed;
        source.sendSuccess(() -> Component.literal(
                "TACZ 配件插入完成：成功 " + finalInserted + " 种，失败 " + finalFailed + " 种"
                        + "（每种 " + count + " 个，netId=" + netId + "）"), false);
        return inserted > 0 ? 1 : 0;
    }


    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildItemInsertCommand(CommandBuildContext context) {
        return Commands.literal("item")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.item_required")); return 0; })
                .then(Commands.argument("item", ItemArgument.item(context))
                        .executes(ctx -> executeInsertItemDefault(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                        .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                .executes(ctx -> executeInsertItemDefault(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, false), LongArgumentType.getLong(ctx, "count")))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), LongArgumentType.getLong(ctx, "count"))))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.item_required")); return 0; })
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), LongArgumentType.getLong(ctx, "count"))))));
    }

    /**
     * 物品标签批量插入命令
     * 用法：insert tag <#标签> [每种数量] [netId] 或 insert tag <netId> <#标签> [每种数量]
     * 标签内每种物品各插入 count 个，空间不足的种类自动跳过，结果以列表逐种输出
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTagInsertCommand(CommandBuildContext context) {
        return Commands.literal("tag")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.tag_required")); return 0; })
                .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                        .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"), 1))
                        .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count")))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"), getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.tag_required")); return 0; })
                        .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                                .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"), getItemTag(ctx, "tag"), 1))
                                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"), getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))));
    }

    /** 从命令上下文解析物品标签；输入为单个物品 ID（无 # 前缀）时提示需要标签 */
    private static TagKey<Item> getItemTag(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        var result = ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, name, Registries.ITEM,
                new DynamicCommandExceptionType(arg -> CommandLang.component("error.tag_required")));
        if (result.unwrap().right().isEmpty()) {
            throw new SimpleCommandExceptionType(CommandLang.component("error.tag_required")).create();
        }
        return result.unwrap().right().get();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildFluidInsertCommand(CommandBuildContext context) {
        return Commands.literal("fluid")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.fluid_required")); return 0; })
                .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                        .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluidDefault(ctx, h.value(), 1000L); })
                        .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluidDefault(ctx, h.value(), LongArgumentType.getLong(ctx, "amount")); })
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"), h.value(), LongArgumentType.getLong(ctx, "amount")); }))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.fluid_required")); return 0; })
                        .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                                .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"), h.value(), 1000L); })
                                .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                        .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"), h.value(), LongArgumentType.getLong(ctx, "amount")); }))));
    }

    // ========== 药水护符插入（仅神化 Apotheosis 加载时注册） ==========

    /** 神化药水护符物品 ID。 */
    private static final ResourceLocation APOTH_POTION_CHARM_ID =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "potion_charm");
    /** 神化护符启用组件 ID（对应 1.20.1 的 NBT {@code charm_enabled}）。 */
    private static final ResourceLocation APOTH_CHARM_ENABLED_ID =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "charm_enabled");

    /**
     * 构建药水护符批量插入命令（需加载神化 Apotheosis）。
     * <p>用法：{@code insert potionCharm <potion|all> [count] [plain|mending|unbreakable] [netId]}。
     * 默认：数量 1、plain、当前玩家主网络。生成的护符默认 {@code charm_enabled=true}。
     * {@code all} 表示对全部"合法药水"（单条非即时效果）各插入一批。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildPotionCharmInsertCommand(
            CommandBuildContext context) {
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

    /** 从命令上下文解析药水。 */
    private static Holder<Potion> getPotion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "potion", Registries.POTION);
    }

    /** 执行药水护符批量插入。 */
    private static int executeInsertPotionCharm(CommandContext<CommandSourceStack> ctx, Holder<Potion> potionHolder,
                                                int count, String mode, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;

        DimensionsNet net;
        int actualNetId;
        if (netId < 0) {
            ServerPlayer executor = source.getPlayer();
            if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
            net = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (net == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
            actualNetId = net.getId();
        } else {
            net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) return 0;
            actualNetId = netId;
        }

        Item charmItem = BuiltInRegistries.ITEM.get(APOTH_POTION_CHARM_ID);
        if (charmItem == Items.AIR) {
            source.sendFailure(OutputFormatter.createError("error.apotheosis_required"));
            return 0;
        }

        ItemStack prototype = buildPotionCharmStack(source, charmItem, potionHolder, mode);
        ItemStackKey key = new ItemStackKey(prototype.copyWithCount(1));
        if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) {
            source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage"));
            return 0;
        }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
        long inserted = count - remaining.amount();
        if (inserted <= 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        net.setDirty();

        String potionName = BuiltInRegistries.POTION.getKey(potionHolder.value()).toString();
        final int finalNetId = actualNetId;
        source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.insert.potionCharm.success", inserted, potionName, mode, finalNetId)), false);
        return 1;
    }

    /**
     * 构造带指定药水与模式的药水护符（1.21.1 组件版）：
     * 药水写入 {@code POTION_CONTENTS}；启用标记同时写神化组件 {@code apotheosis:charm_enabled}
     * 与自定义 NBT {@code charm_enabled}（本模组网络护符逻辑读 NBT）；
     * unbreakable 写 {@code UNBREAKABLE} 组件 + NBT {@code Unbreakable}，mending 写入 1 级经验修补附魔。
     */
    @SuppressWarnings("unchecked")
    private static ItemStack buildPotionCharmStack(CommandSourceStack source, Item charmItem,
                                                   Holder<Potion> potion, String mode) {
        ItemStack stack = new ItemStack(charmItem);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        DataComponentType<Boolean> charmEnabled =
                (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(APOTH_CHARM_ENABLED_ID);
        if (charmEnabled != null) {
            stack.set(charmEnabled, Boolean.TRUE);
        }
        ItemNbt.update(stack, tag -> {
            tag.putBoolean("charm_enabled", true);
            if ("unbreakable".equals(mode)) {
                tag.putBoolean("Unbreakable", true);
            }
        });
        if ("unbreakable".equals(mode)) {
            stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        } else if ("mending".equals(mode)) {
            Holder<net.minecraft.world.item.enchantment.Enchantment> mending = source.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.MENDING);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(mending, 1);
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
        return stack;
    }

    /** 执行"批量插入合法药水护符"：按类别（all/positive/negative）筛选后，每个药水各插入 count 个；空间不足的种类跳过。 */
    private static int executeInsertAllPotionCharms(CommandContext<CommandSourceStack> ctx, int count, String mode,
                                                    int netId, String category) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;

        DimensionsNet net;
        int actualNetId;
        if (netId < 0) {
            ServerPlayer executor = source.getPlayer();
            if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
            net = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (net == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
            actualNetId = net.getId();
        } else {
            net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) return 0;
            actualNetId = netId;
        }

        Item charmItem = BuiltInRegistries.ITEM.get(APOTH_POTION_CHARM_ID);
        if (charmItem == Items.AIR) {
            source.sendFailure(OutputFormatter.createError("error.apotheosis_required"));
            return 0;
        }

        int types = 0;
        long total = 0;
        for (Potion potion : BuiltInRegistries.POTION.stream().toList()) {
            if (!isValidCharmPotion(potion) || !matchesCategory(potion, category)) continue;
            Holder<Potion> holder = BuiltInRegistries.POTION.wrapAsHolder(potion);
            ItemStackKey key = new ItemStackKey(buildPotionCharmStack(source, charmItem, holder, mode).copyWithCount(1));
            if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) continue;
            KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
            long inserted = count - remaining.amount();
            if (inserted > 0) {
                types++;
                total += inserted;
            }
        }
        if (total <= 0) {
            source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage"));
            return 0;
        }
        net.setDirty();

        final int fTypes = types;
        final long fTotal = total;
        final int fNetId = actualNetId;
        source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.insert.potionCharm.all.success", fTypes, fTotal, mode, fNetId,
                        CommandLang.get("network.insert.potionCharm.category." + category))), false);
        return 1;
    }

    /** 是否为可做成护符的"合法药水"：恰好一条非即时效果（与神化 PotionCharmItem.isValidPotion 核心一致）。 */
    private static boolean isValidCharmPotion(Potion potion) {
        var effects = potion.getEffects();
        return effects.size() == 1 && !effects.get(0).getEffect().value().isInstantenous();
    }

    /** 按效果类别筛选：all=全部；positive=正面(BENEFICIAL)；negative=负面(HARMFUL)；NEUTRAL 仅归入 all。 */
    private static boolean matchesCategory(Potion potion, String category) {
        if (category == null || "all".equals(category)) return true;
        var effectCategory = potion.getEffects().get(0).getEffect().value().getCategory();
        if ("positive".equals(category)) return effectCategory == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL;
        if ("negative".equals(category)) return effectCategory == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
        return true;
    }

    /**
     * 能量插入命令（仅能量键形态）。
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

    // ========== Item ==========

    private static int executeInsertItemDefault(CommandContext<CommandSourceStack> ctx, ItemStack stack, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertItemInternal(source, primaryNet, primaryNet.getId(), stack, count);
    }

    private static int executeInsertItem(CommandContext<CommandSourceStack> ctx, int netId, ItemStack stack, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertItemInternal(source, net, netId, stack, count);
    }

    private static int executeInsertItemInternal(CommandSourceStack source, DimensionsNet net, int netId, ItemStack stack, long count) {
        if (!PermissionChecker.checkAmountPositive(source, count)) return 0;
        ItemStackKey key = new ItemStackKey(stack.copyWithCount(1));
        if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) { source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage")); return 0; }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
        if (remaining.amount() > 0) { source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount())); return 0; }
        net.setDirty();
        String name = stack.getHoverName().getString();
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.insert.item.success", count, name, netId)), false);
        return 1;
    }

    // ========== Tag ==========

    private static int executeInsertTagDefault(CommandContext<CommandSourceStack> ctx, TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertTagInternal(source, primaryNet, primaryNet.getId(), tag, count);
    }

    private static int executeInsertTag(CommandContext<CommandSourceStack> ctx, int netId, TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertTagInternal(source, net, netId, tag, count);
    }

    /**
     * 按物品标签批量插入，结果以列表逐种输出：
     * 标题行 + 每种物品一行（成功显示物品与数量，跳过显示原因）+ 汇总行。
     */
    private static int executeInsertTagInternal(CommandSourceStack source, DimensionsNet net, int netId, TagKey<Item> tag, long count) {
        if (!PermissionChecker.checkAmountPositive(source, count)) return 0;

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

            final long shown = inserted;
            if (inserted > 0) {
                source.sendSuccess(() -> OutputFormatter.createItemDisplay(stack, shown), false);
            } else {
                source.sendSuccess(() -> OutputFormatter.createWarning("network.insert.tag.line.skipped",
                        stack.getHoverName().getString()), false);
            }
        }

        if (totalInserted <= 0) {
            source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage"));
            return 0;
        }

        net.setDirty();

        // 汇总行
        final int okTypes = insertedTypes;
        final int skipTypes = skippedTypes;
        final long sumInserted = totalInserted;
        source.sendSuccess(() -> OutputFormatter.createSuccess("network.insert.tag.summary",
                okTypes, items.size(), count, sumInserted, skipTypes), false);

        return 1;
    }

    // ========== Fluid ==========

    private static int executeInsertFluidDefault(CommandContext<CommandSourceStack> ctx, Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertFluidInternal(source, primaryNet, primaryNet.getId(), fluid, amount);
    }

    private static int executeInsertFluid(CommandContext<CommandSourceStack> ctx, int netId, Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertFluidInternal(source, net, netId, fluid, amount);
    }

    private static int executeInsertFluidInternal(CommandSourceStack source, DimensionsNet net, int netId, Fluid fluid, long amount) {
        if (!PermissionChecker.checkAmountPositive(source, amount)) return 0;
        FluidStack fs = new FluidStack(fluid, 1);
        FluidStackKey key = new FluidStackKey(fs);
        if (!NetworkUtils.hasEnoughStorageForFluid(net, key, amount)) { source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage")); return 0; }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, amount, false);
        if (remaining.amount() > 0) { source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount())); return 0; }
        net.setDirty();
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.insert.fluid.success", amount, fluid.getFluidType().getDescription().getString(), netId)), false);
        return 1;
    }

    // ========== Energy ==========

    /**
     * 执行"能量键插入"：从 BD 注册表按类型 ID 解析存储键并插入指定数量。
     * netId&lt;0 时使用执行者主网络；键未注册时提示错误。
     */
    private static int executeInsertEnergyKey(CommandContext<CommandSourceStack> ctx,
                                              ResourceLocation keyId, long amount, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;

        DimensionsNet net;
        int actualNetId;
        if (netId < 0) {
            ServerPlayer executor = source.getPlayer();
            if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
            net = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (net == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
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

        if (!PermissionChecker.checkAmountPositive(source, amount)) return 0;
        if (!NetworkUtils.hasEnoughStorageForKey(net, key, amount)) {
            source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage"));
            return 0;
        }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, amount, false);
        if (remaining.amount() > 0) { source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount())); return 0; }
        net.setDirty();

        final int fNetId = actualNetId;
        final String keyName = key.getTypeId().toString();
        source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.insert.energy.key.success", amount, keyName, fNetId)), false);
        return 1;
    }
}

