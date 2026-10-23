package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.CommandUtils;
import com.solr98.beyondintegration.command.util.OutputFormatter;
import com.solr98.beyondintegration.command.util.PermissionChecker;
import com.solr98.beyondintegration.feature.soul.SoulArkActivation;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 网络"功能激活"统一管理命令（OP），把需要献祭/仪式激活的功能合并到同一分支操作：
 * <pre>
 * /bdtools network activation activate soul [netId]                 激活网络灵魂源
 * /bdtools network activation activate workstation &lt;id|all&gt; [netId] 激活工作台（单台 / 全部）
 * /bdtools network activation reset    soul [netId]                 重置灵魂源激活
 * /bdtools network activation reset    workstation &lt;id|all&gt; [netId] 重置工作台激活
 * </pre>
 * netId 省略时作用于执行者的主要网络。
 */
public class NetworkActivationCommand {

    /** 工作台 ID 补全（含 all） */
    private static final SuggestionProvider<CommandSourceStack> WS_SUGGESTIONS = (ctx, builder) -> {
        for (String id : WorkstationActivation.activatableIds()) builder.suggest(id);
        builder.suggest("all");
        return builder.buildFuture();
    };

    private NetworkActivationCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("activation")
            .requires(source -> CommandUtils.hasOpPermission(source))
            // ===== 激活 =====
            .then(Commands.literal("activate")
                .then(Commands.literal("soul")
                    .executes(ctx -> execSoulActivate(ctx, -1))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> execSoulActivate(ctx, IntegerArgumentType.getInteger(ctx, "netId")))))
                .then(Commands.literal("workstation")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(WS_SUGGESTIONS)
                        .executes(ctx -> execWsActivate(ctx, StringArgumentType.getString(ctx, "id"), -1))
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                            .executes(ctx -> execWsActivate(ctx, StringArgumentType.getString(ctx, "id"),
                                    IntegerArgumentType.getInteger(ctx, "netId")))))))
            // ===== 重置 =====
            .then(Commands.literal("reset")
                .then(Commands.literal("soul")
                    .executes(ctx -> execSoulReset(ctx, -1))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> execSoulReset(ctx, IntegerArgumentType.getInteger(ctx, "netId")))))
                .then(Commands.literal("workstation")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(WS_SUGGESTIONS)
                        .executes(ctx -> execWsReset(ctx, StringArgumentType.getString(ctx, "id"), -1))
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                            .executes(ctx -> execWsReset(ctx, StringArgumentType.getString(ctx, "id"),
                                    IntegerArgumentType.getInteger(ctx, "netId")))))));
    }

    /** 解析目标网络：netId < 0 时用执行者主网络 */
    private static DimensionsNet resolveNet(CommandSourceStack source, int netId) {
        if (netId >= 0) return PermissionChecker.checkNetworkExists(source, netId);
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return null;
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (net == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return null;
        }
        return net;
    }

    // ===== 灵魂源 =====

    private static int execSoulActivate(CommandContext<CommandSourceStack> ctx, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolveNet(source, netId);
        if (net == null) return 0;
        SoulArkActivation.Result result = SoulArkActivation.tryActivate(source.getPlayer(), net);
        if (result == SoulArkActivation.Result.OK) return 1;
        if (result == SoulArkActivation.Result.ALREADY) {
            source.sendSuccess(() -> SoulArkActivation.message(result), false);
            return 0;
        }
        source.sendFailure(SoulArkActivation.message(result));
        return 0;
    }

    private static int execSoulReset(CommandContext<CommandSourceStack> ctx, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolveNet(source, netId);
        if (net == null) return 0;
        boolean reset = SoulArkActivation.reset(net);
        if (reset) {
            source.sendSuccess(() -> Component.literal(
                    CommandLang.get("network.activation.soul.reset", net.getId())), true);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.activation.soul.not_activated", net.getId())), false);
        return 0;
    }

    // ===== 工作台 =====

    private static int execWsActivate(CommandContext<CommandSourceStack> ctx, String id, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = resolveNet(source, netId);
        if (net == null) return 0;

        if ("all".equalsIgnoreCase(id)) {
            int ok = 0;
            for (String ws : WorkstationActivation.activatableIds()) {
                if (WorkstationActivation.tryActivate(net, ws) == WorkstationActivation.Result.SUCCESS) ok++;
            }
            final int n = ok;
            if (n > 0) {
                source.sendSuccess(() -> Component.literal(
                        CommandLang.get("network.activation.workstation.activate.all", net.getId(), n)), true);
                return 1;
            }
            source.sendFailure(OutputFormatter.createError(
                    "network.activation.workstation.activate.none", net.getId()));
            return 0;
        }

        if (!WorkstationActivation.isActivatable(id)) {
            source.sendFailure(OutputFormatter.createError("network.activation.workstation.invalid", id));
            return 0;
        }
        WorkstationActivation.Result result = WorkstationActivation.tryActivate(net, id);
        switch (result) {
            case SUCCESS -> {
                source.sendSuccess(() -> Component.literal(
                        CommandLang.get("network.activation.workstation.activate.one", net.getId(), id)), true);
                return 1;
            }
            case ALREADY -> {
                source.sendSuccess(() -> Component.literal(
                        CommandLang.get("network.activation.workstation.activate.already", net.getId(), id)), false);
                return 0;
            }
            case DISABLED -> {
                source.sendFailure(OutputFormatter.createError("network.activation.workstation.disabled"));
                return 0;
            }
            case NO_ITEM -> {
                source.sendFailure(OutputFormatter.createError(
                        "network.activation.workstation.no_item", net.getId(), id));
                return 0;
            }
            default -> {
                source.sendFailure(OutputFormatter.createError("network.activation.workstation.invalid", id));
                return 0;
            }
        }
    }

    private static int execWsReset(CommandContext<CommandSourceStack> ctx, String id, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        boolean all = "all".equalsIgnoreCase(id);
        String targetId = all ? null : id;
        if (!all && !WorkstationActivation.isActivatable(id)) {
            source.sendFailure(OutputFormatter.createError("network.activation.workstation.invalid", id));
            return 0;
        }
        DimensionsNet net = resolveNet(source, netId);
        if (net == null) return 0;

        List<String> removed = WorkstationActivation.reset(net, targetId);
        if (removed.isEmpty()) {
            source.sendSuccess(() -> Component.literal(CommandLang.get(
                    all ? "network.workstation.reset.all_none" : "network.workstation.reset.none",
                    net.getId(), id)), false);
            return 0;
        }
        if (all) {
            source.sendSuccess(() -> Component.literal(CommandLang.get(
                    "network.workstation.reset.all", net.getId(), removed.size(), String.join(", ", removed))), true);
        } else {
            source.sendSuccess(() -> Component.literal(CommandLang.get(
                    "network.workstation.reset.one", net.getId(), id)), true);
        }
        return 1;
    }
}
