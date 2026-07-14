package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.AuditFormat;
import com.solr98.beyondintegration.command.util.OutputFormatter;
import com.solr98.beyondintegration.command.util.PaginationUtil;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.NetworkMeters;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class AuditQueryCommand {

    private static final int PAGE_SIZE = 10;

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("audit")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list")
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0))
                                .executes(ctx -> listLog(ctx, 0, null))
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(ctx -> listLog(ctx, IntegerArgumentType.getInteger(ctx, "page"), null))
                                        .then(Commands.argument("filter", StringArgumentType.word())
                                                .executes(ctx -> listLog(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "page"),
                                                        StringArgumentType.getString(ctx, "filter")))))))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> playerLog(ctx, 0))
                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                        .executes(ctx -> playerLog(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("meter")
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0))
                                .executes(AuditQueryCommand::showMeter)))
                .then(Commands.literal("recent")
                        .executes(ctx -> recentLog(ctx, 5)))
                .then(Commands.literal("token")
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0))
                                .executes(AuditQueryCommand::showToken)))
                .then(Commands.literal("tokens")
                        .requires(src -> src.hasPermission(3))
                        .executes(ctx -> { ctx.getSource().sendFailure(Component.literal(CommandLang.get("audit.token_usage"))); return 0; }));
    }

    private static int listLog(CommandContext<CommandSourceStack> ctx, int page, String filter) {
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        BindingAuditLog auditLog = BindingAuditLog.getInstance();
        if (auditLog == null) {
            ctx.getSource().sendFailure(OutputFormatter.createError("audit.log_not_available"));
            return 0;
        }

        List<AuditEntry> entries = auditLog.queryByNet(netId, page, PAGE_SIZE);
        if (filter != null && !filter.isEmpty()) {
            entries = entries.stream()
                    .filter(e -> e.action().equalsIgnoreCase(filter))
                    .toList();
        }
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("audit.no_entries", netId)).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> OutputFormatter.createTitle("audit.title", netId, page), false);
        for (AuditEntry e : entries) {
            String time = AuditFormat.time(e.timestamp());
            String status = AuditFormat.status(e.success());
            String tgt = AuditFormat.targetFull(e.targetType(), e.targetInfo(), e.detail());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("§7[%s] %s §e%s§r %s%s", time, status, AuditFormat.action(e.action()), AuditFormat.by(e.playerName()), tgt)), false);
        }
        String countSuffix = filter != null ? " (filter: " + filter + ")" : "";
        int totalCount = auditLog.countByNet(netId);
        int totalPages = Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        ctx.getSource().sendSuccess(() -> PaginationUtil.createPagination(
                "/bdtools audit list " + netId, page, totalPages, totalCount), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int playerLog(CommandContext<CommandSourceStack> ctx, int page) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            BindingAuditLog auditLog = BindingAuditLog.getInstance();
            if (auditLog == null) {
                ctx.getSource().sendFailure(OutputFormatter.createError("audit.log_not_available"));
                return 0;
            }

            List<AuditEntry> entries = auditLog.queryByPlayer(target.getName().getString(), page, PAGE_SIZE);
            if (entries.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("audit.no_player_entries", target.getName().getString())).withStyle(ChatFormatting.GRAY), false);
                return 0;
            }

            ctx.getSource().sendSuccess(() -> OutputFormatter.createTitle("audit.player_title", target.getName().getString(), page), false);
            for (AuditEntry e : entries) {
                String time = AuditFormat.time(e.timestamp());
                String status = AuditFormat.status(e.success());
                String tgt = AuditFormat.targetFull(e.targetType(), e.targetInfo(), e.detail());
                ctx.getSource().sendSuccess(() -> Component.literal(
                        String.format("§7[%s] %s §e%s§r #%d %s", time, status, AuditFormat.action(e.action()), e.netId(), tgt)), false);
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(OutputFormatter.createError("audit.player_not_found"));
            return 0;
        }
    }

    private static int showMeter(CommandContext<CommandSourceStack> ctx) {
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        NetworkMeters.NetworkMeterGroup meters = NetworkMeters.getNetworkMeters(netId);
        if (meters == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("audit.no_meter", netId)).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> OutputFormatter.createTitle("audit.meter_title", netId), false);

        for (var ifEntry : meters.interfaces.entrySet()) {
            BlockPos pos = ifEntry.getKey();
            NetworkMeters.InterfaceMeter im = ifEntry.getValue();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    CommandLang.get("audit.meter.interface", pos.toShortString(), im.totalOps.get())).withStyle(ChatFormatting.WHITE), false);
            for (var item : im.totals.entrySet()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        CommandLang.get("audit.meter.item", item.getKey().toString(), item.getValue())).withStyle(ChatFormatting.YELLOW), false);
            }
        }

        for (var cellEntry : meters.cells.entrySet()) {
            String cellStr = cellEntry.getKey().toString().substring(0, 8);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    CommandLang.get("audit.meter.cell", cellStr)).withStyle(ChatFormatting.WHITE), false);
            NetworkMeters.CellMeter cm = cellEntry.getValue();
            for (var item : cm.inserted.entrySet()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        CommandLang.get("audit.meter.in", item.getKey().toString(), item.getValue())).withStyle(ChatFormatting.GREEN), false);
            }
            for (var item : cm.extracted.entrySet()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        CommandLang.get("audit.meter.out", item.getKey().toString(), item.getValue())).withStyle(ChatFormatting.RED), false);
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int recentLog(CommandContext<CommandSourceStack> ctx, int count) {
        BindingAuditLog auditLog = BindingAuditLog.getInstance();
        if (auditLog == null) {
            ctx.getSource().sendFailure(OutputFormatter.createError("audit.log_not_available"));
            return 0;
        }
        List<AuditEntry> entries = auditLog.queryRecent(count);
        ctx.getSource().sendSuccess(() -> OutputFormatter.createTitle("audit.recent_title"), false);
        for (AuditEntry e : entries) {
            String time = AuditFormat.time(e.timestamp());
            String status = AuditFormat.status(e.success());
            String tgt = AuditFormat.targetFull(e.targetType(), e.targetInfo(), e.detail());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("§7[%s] %s §e%s§r #%d %s %s", time, status, AuditFormat.action(e.action()), e.netId(), AuditFormat.by(e.playerName()), tgt)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showToken(CommandContext<CommandSourceStack> ctx) {
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            ctx.getSource().sendFailure(OutputFormatter.createError("binding.network_not_found", netId));
            return 0;
        }
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        java.util.UUID token = BindingTokenManager.getOrCreateToken(netId, net.getOwner());
        ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("audit.token.title", netId, netName)).withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("audit.token.value", token.toString())).withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("audit.token.owner", net.getOwner().toString())), false);
        return Command.SINGLE_SUCCESS;
    }
}
