package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import net.minecraft.server.level.ServerPlayer;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NetworkResetBindingCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("resetBinding")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0))
                        .executes(NetworkResetBindingCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        CommandSourceStack src = ctx.getSource();

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            src.sendFailure(Component.translatable("message.beyond_integration.network_not_found", netId));
            return 0;
        }

        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        if (!net.isOwner(player) && !src.hasPermission(3)) {
            src.sendFailure(Component.translatable("message.beyond_integration.no_permission"));
            return 0;
        }

        BindingTokenManager.resetToken(netId, player.getUUID());
        String netName = net instanceof NetworkNameProvider nnp
                ? nnp.getCustomName() : String.valueOf(netId);

        src.sendSuccess(() -> Component.translatable("command.beyond_integration.network.reset.success", netName, netId), true);

        return Command.SINGLE_SUCCESS;
    }
}
