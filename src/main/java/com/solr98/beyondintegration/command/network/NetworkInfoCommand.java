package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import com.mojang.brigadier.arguments.IntegerArgumentType;

public class NetworkInfoCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("info")
            .executes(ctx -> execute(ctx, -1, null))
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                            EntityArgument.getPlayer(ctx, "player")))
                )
            );
    }

    public static int execute(CommandContext<CommandSourceStack> ctx, int netId, ServerPlayer targetPlayer) {
        CommandSourceStack source = ctx.getSource();

        if (!PermissionChecker.checkServerAvailable(source)) return 0;

        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(OutputFormatter.createError("error.server_not_available"));
            return 0;
        }

        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }

        if (netId == -1) {
            DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (primaryNet == null) {
                source.sendFailure(OutputFormatter.createError("error.not_in_network"));
                return 0;
            }
            netId = primaryNet.getId();
        }

        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;

        ServerPlayer playerToCheck = targetPlayer != null ? targetPlayer : executor;
        if (targetPlayer != null && !executor.getUUID().equals(targetPlayer.getUUID())) {
            if (!PermissionChecker.checkOpPermissionForOthers(source, targetPlayer)) return 0;
        }

        if (!PermissionChecker.checkNetworkAccessPermission(source, net, playerToCheck)) return 0;

        MutableComponent message = buildNetworkInfoMessage(net, playerToCheck, server);
        source.sendSuccess(() -> message, false);
        return 1;
    }

    public static MutableComponent buildNetworkInfoMessage(DimensionsNet net, ServerPlayer player,
                                                           net.minecraft.server.MinecraftServer server) {
        MutableComponent message = Component.empty();

        message = message.append(OutputFormatter.createTitle("network.info.title", net.getId()))
                .append(Component.literal("\n"));

        NetworkUtils.NetworkStats stats = NetworkUtils.getNetworkStats(net);
        String permissionLevel = NetworkUtils.getPlayerPermissionLevel(player, net);
        String permissionDisplay = NetworkUtils.getPermissionLevelDisplay(permissionLevel);
        String ownerName = CommandUtils.getNetworkOwnerName(net, server);

        message = message.append(Component.literal(CommandLang.get("network.info.owner_label", ownerName)))
                .append(Component.literal(net.deleted ?
                        CommandLang.get("network.info.status.deleted") :
                        CommandLang.get("network.info.status.active"))
                        .withStyle(net.deleted ? ChatFormatting.RED : ChatFormatting.GREEN))
                .append(Component.literal(CommandLang.get("network.info.your_permission_label")))
                .append(Component.literal(permissionDisplay)
                        .withStyle(getPermissionColor(permissionLevel)))
                .append(Component.literal("\n"));

        int remainingTime = NetworkUtils.getCrystalRemainingTime(net);
        message = message.append(Component.literal(CommandLang.get("network.info.crystal_time")))
                .append(OutputFormatter.createHoverableTime(remainingTime))
                .append(Component.literal("\n"));

        long slotCapacity = net.getUnifiedStorage().slotCapacity;
        int slotMaxSize = net.getUnifiedStorage().slotMaxSize;

        message = message.append(Component.literal(CommandLang.get("network.info.slot_capacity_label")))
                .append(OutputFormatter.createHoverableNumber(slotCapacity,
                        CommandLang.get("network.info.slot_capacity_label")))
                .append(Component.literal(CommandLang.get("network.info.slot_count_label")))
                .append(OutputFormatter.createHoverableNumber(slotMaxSize,
                        CommandLang.get("network.info.slot_count_label")))
                .append(Component.literal("\n"));

        message = message.append(Component.literal(CommandLang.get("network.info.storage_stats") + "\n"));

        if (stats.itemTypes > 0) {
            message = message.append(Component.literal(CommandLang.get("network.info.items_label")))
                    .append(OutputFormatter.createHoverableResourceType(stats.itemTypes,
                            CommandLang.get("network.info.items_label").trim()))
                    .append(Component.literal(CommandLang.get("network.info.types_suffix")))
                    .append(OutputFormatter.createHoverableItemCount(stats.itemTotal))
                    .append(Component.literal("\n"));
        }

        if (stats.fluidTypes > 0) {
            message = message.append(Component.literal(CommandLang.get("network.info.fluids_label")))
                    .append(OutputFormatter.createHoverableResourceType(stats.fluidTypes,
                            CommandLang.get("network.info.fluids_label").trim()))
                    .append(Component.literal(CommandLang.get("network.info.types_suffix")))
                    .append(OutputFormatter.createHoverableFluid(stats.fluidTotal))
                    .append(Component.literal(" mB\n"));
        }

        if (stats.energyTypes > 0) {
            message = message.append(Component.literal(CommandLang.get("network.info.energy_label")))
                    .append(OutputFormatter.createHoverableResourceType(stats.energyTypes,
                            CommandLang.get("network.info.energy_label").trim()))
                    .append(Component.literal(CommandLang.get("network.info.types_suffix")))
                    .append(OutputFormatter.createHoverableEnergy(stats.energyTotal))
                    .append(Component.literal(" FE\n"));
        }

        if (stats.getTotalTypes() == 0) {
            message = message.append(Component.literal(CommandLang.get("network.info.no_resources")).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n"));
        }

        int playerCount = net.getPlayers().size();
        int managerCount = net.getManagers().size();

        message = message.append(Component.literal(CommandLang.get("network.info.player_count_label")))
                .append(OutputFormatter.createHoverableNumber(playerCount,
                        CommandLang.get("network.info.player_count_label")))
                .append(Component.literal(CommandLang.get("network.info.manager_count_label")))
                .append(OutputFormatter.createHoverableNumber(managerCount,
                        CommandLang.get("network.info.manager_count_label")))
                .append(Component.literal("\n"));

        message = message.append(Component.literal(CommandLang.get("network.info.player_list_label")));

        NetworkUtils.PlayerList playerList = NetworkUtils.getNetworkPlayerList(net, server);
        if (playerList.hasPlayers()) {
            message = message.append(OutputFormatter.createPlayerList(playerList));
        } else {
            message = message.append(Component.literal(CommandLang.get("network.info.no_players"))
                    .withStyle(ChatFormatting.GRAY));
        }

        return message;
    }

    public static ChatFormatting getPermissionColor(String permissionLevel) {
        switch (permissionLevel) {
            case "owner": return ChatFormatting.RED;
            case "manager": return ChatFormatting.BLUE;
            case "member": return ChatFormatting.GREEN;
            default: return ChatFormatting.GRAY;
        }
    }
}
