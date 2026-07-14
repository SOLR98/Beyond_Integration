package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public class TokenCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("token")
                .then(Commands.literal("list")
                        .executes(TokenCommand::listOwn)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(TokenCommand::listOther)))
                .then(Commands.literal("reset")
                        .executes(TokenCommand::resetSelf)
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0))
                                .executes(TokenCommand::resetSpecific)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(TokenCommand::resetOther))));
    }

    private static int listOwn(CommandContext<CommandSourceStack> ctx) {
        if (!CommandConfig.enableTokenSystem()) {
            ctx.getSource().sendFailure(Component.literal("Token system is disabled."));
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Player only"));
            return 0;
        }
        var nets = DimensionsNet.getAllNetFromPlayer(player);
        if (nets.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§6You have no networks"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== My Network Tokens ==="), false);
        for (DimensionsNet net : nets) {
            int id = net.getId();
            String name = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            UUID t = BindingTokenManager.getToken(id, player.getUUID());
            final UUID myToken = t != null ? t : BindingTokenManager.getOrCreateToken(id, player.getUUID());
            String tokenStr = myToken.toString().substring(0, 8) + "...";
            String perm = net.isOwner(player) ? "§a[Owner]" : net.isManager(player) ? "§b[Manager]" : "§7[Member]";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("§b#%d §e%s %s §8Token: %s", id, name, perm, tokenStr)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listOther(CommandContext<CommandSourceStack> ctx) {
        if (!CommandConfig.enableTokenSystem()) {
            ctx.getSource().sendFailure(Component.literal("Token system is disabled."));
            return 0;
        }
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            var nets = DimensionsNet.getAllNetFromPlayer(target);
            if (nets.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§6No networks for " + target.getName().getString()), false);
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§6=== " + target.getName().getString() + "'s Network Tokens ==="), false);
            for (DimensionsNet net : nets) {
                int id = net.getId();
                String name = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
                UUID t = BindingTokenManager.getToken(id, target.getUUID());
                final UUID token2 = t != null ? t : BindingTokenManager.getOrCreateToken(id, target.getUUID());
                ctx.getSource().sendSuccess(() -> Component.literal(
                        String.format("§b#%d §e%s §8Token: %s", id, name, token2)), false);
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int resetSelf(CommandContext<CommandSourceStack> ctx) {
        if (!CommandConfig.enableTokenSystem()) {
            ctx.getSource().sendFailure(Component.literal("Token system is disabled."));
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Player only. Usage: /bdtools token reset <netId>"));
            return 0;
        }
        ctx.getSource().sendFailure(Component.literal("Usage: /bdtools token reset <netId> [player]"));
        return 0;
    }

    private static int resetSpecific(CommandContext<CommandSourceStack> ctx) {
        if (!CommandConfig.enableTokenSystem()) {
            ctx.getSource().sendFailure(Component.literal("Token system is disabled."));
            return 0;
        }
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            ctx.getSource().sendFailure(Component.literal("Network #" + netId + " not found"));
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Player only"));
            return 0;
        }
        // Player resets their own token
        if (!net.isManager(player)) {
            ctx.getSource().sendFailure(Component.literal("You are not a member of this network."));
            return 0;
        }
        UUID newToken = BindingTokenManager.resetToken(netId, player.getUUID());
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : String.valueOf(netId);
        ctx.getSource().sendSuccess(() -> Component.literal("§aYour token for network " + netName + " (ID: " + netId + ") has been reset. Old devices will need to be rebound.").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetOther(CommandContext<CommandSourceStack> ctx) {
        if (!CommandConfig.enableTokenSystem()) {
            ctx.getSource().sendFailure(Component.literal("Token system is disabled."));
            return 0;
        }
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            ctx.getSource().sendFailure(Component.literal("Network #" + netId + " not found"));
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer owner)) {
            ctx.getSource().sendFailure(Component.literal("Player only"));
            return 0;
        }
        if (!net.isOwner(owner) && !ctx.getSource().hasPermission(3)) {
            ctx.getSource().sendFailure(Component.literal("Only the network owner can reset other players' tokens."));
            return 0;
        }
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            UUID newToken = BindingTokenManager.resetToken(netId, target.getUUID());
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : String.valueOf(netId);
            ctx.getSource().sendSuccess(() -> Component.literal("§aToken for " + target.getName().getString() + " on network " + netName + " has been reset.").withStyle(ChatFormatting.GREEN), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
