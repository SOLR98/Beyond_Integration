package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.util.PermissionChecker;
import com.solr98.beyondintegration.feature.soul.SoulArkActivation;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /bdtools network soul ...}：网络灵魂源管理（OP）。
 * <ul>
 *   <li>{@code get [netId]} 查询魂量与激活状态；</li>
 *   <li>{@code set <amount> [netId]} / {@code add <amount> [netId]} 设置/增加魂量；</li>
 *   <li>{@code ark activate [netId]} 献祭网络方舟激活；{@code ark reset [netId]} 重置。</li>
 * </ul>
 */
public final class NetworkSoulCommand {

    private NetworkSoulCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("soul")
                .then(Commands.literal("get")
                        .executes(ctx -> execGet(ctx, -1))
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                .executes(ctx -> execGet(ctx, IntegerArgumentType.getInteger(ctx, "netId")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                .executes(ctx -> execSet(ctx, LongArgumentType.getLong(ctx, "amount"), -1))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> execSet(ctx, LongArgumentType.getLong(ctx, "amount"),
                                                IntegerArgumentType.getInteger(ctx, "netId"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                .executes(ctx -> execAdd(ctx, LongArgumentType.getLong(ctx, "amount"), -1))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> execAdd(ctx, LongArgumentType.getLong(ctx, "amount"),
                                                IntegerArgumentType.getInteger(ctx, "netId"))))))
                .then(Commands.literal("convert")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> execConvert(ctx, IntegerArgumentType.getInteger(ctx, "amount"), -1))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> execConvert(ctx, IntegerArgumentType.getInteger(ctx, "amount"),
                                                IntegerArgumentType.getInteger(ctx, "netId"))))))
                .then(Commands.literal("ark")
                        .then(Commands.literal("activate")
                                .executes(ctx -> execArkActivate(ctx, -1))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> execArkActivate(ctx, IntegerArgumentType.getInteger(ctx, "netId")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> execArkReset(ctx, -1))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> execArkReset(ctx, IntegerArgumentType.getInteger(ctx, "netId"))))));
    }

    /** 解析目标网络：netId<0 用执行者主网络，否则按 ID 查找（不存在时由 PermissionChecker 提示）。 */
    private static DimensionsNet resolve(CommandSourceStack source, int netId) {
        if (netId < 0) {
            ServerPlayer p = source.getPlayer();
            return p == null ? null : DimensionsNet.getPrimaryNetFromPlayer(p);
        }
        return PermissionChecker.checkNetworkExists(source, netId);
    }

    private static int execGet(CommandContext<CommandSourceStack> ctx, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolve(source, netId);
        if (net == null) {
            source.sendFailure(Component.translatable("message.beyond_integration.soul.net_missing"));
            return 0;
        }
        long souls = SoulEnergyAccess.getSouls(net);
        boolean active = SoulEnergyAccess.isActivated(net);
        source.sendSuccess(() -> Component.literal(
                "soul(net=" + net.getId() + ") = " + souls + (active ? " [已激活]" : " [未激活]")), false);
        return 1;
    }

    private static int execSet(CommandContext<CommandSourceStack> ctx, long amount, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolve(source, netId);
        if (net == null) {
            source.sendFailure(Component.translatable("message.beyond_integration.soul.net_missing"));
            return 0;
        }
        long now = SoulEnergyAccess.setSouls(net, amount);
        net.setDirty();
        source.sendSuccess(() -> Component.literal("soul(net=" + net.getId() + ") = " + now), false);
        return 1;
    }

    private static int execAdd(CommandContext<CommandSourceStack> ctx, long amount, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolve(source, netId);
        if (net == null) {
            source.sendFailure(Component.translatable("message.beyond_integration.soul.net_missing"));
            return 0;
        }
        long added = SoulEnergyAccess.insertSouls(net, amount);
        net.setDirty();
        source.sendSuccess(() -> Component.literal(
                "soul(net=" + net.getId() + ") +" + added + " = " + SoulEnergyAccess.getSouls(net)), false);
        return 1;
    }

    private static int execConvert(CommandContext<CommandSourceStack> ctx, int amount, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("This command requires a player")); return 0; }
        DimensionsNet net = resolve(source, netId);
        if (net == null) { source.sendFailure(Component.translatable("message.beyond_integration.soul.net_missing")); return 0; }
        if (!com.solr98.beyondintegration.feature.soul.SoulManualConvert.goetyLoaded()) {
            source.sendFailure(Component.literal("Goety not loaded")); return 0;
        }
        long added = com.solr98.beyondintegration.feature.soul.SoulManualConvert.convert(player, net, amount);
        source.sendSuccess(() -> Component.literal(
                "convert +" + added + " -> soul(net=" + net.getId() + ") = " + SoulEnergyAccess.getSouls(net)), false);
        return added > 0 ? 1 : 0;
    }

    private static int execArkActivate(CommandContext<CommandSourceStack> ctx, int netId) {        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolve(source, netId);
        SoulArkActivation.Result result = SoulArkActivation.tryActivate(source.getPlayer(), net);
        if (result == SoulArkActivation.Result.OK) {
            return 1; // 已由 SoulArkActivation 向玩家发送提示
        }
        if (result == SoulArkActivation.Result.ALREADY) {
            source.sendSuccess(() -> SoulArkActivation.message(result), false);
            return 1;
        }
        source.sendFailure(SoulArkActivation.message(result));
        return 0;
    }

    private static int execArkReset(CommandContext<CommandSourceStack> ctx, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolve(source, netId);
        if (net == null) {
            source.sendFailure(Component.translatable("message.beyond_integration.soul.net_missing"));
            return 0;
        }
        boolean reset = SoulArkActivation.reset(net);
        source.sendSuccess(() -> Component.literal(
                reset ? "soul ark reset for net " + net.getId() : "soul ark not activated"), false);
        return 1;
    }
}
