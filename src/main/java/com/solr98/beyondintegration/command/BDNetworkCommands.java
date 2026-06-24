package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.command.network.*;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BDNetworkCommands {
    private BDNetworkCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        var c = event.getBuildContext();
        d.register(Commands.literal("bdtools").requires(s -> s.hasPermission(2))
                .then(buildNetworkCommands(c))
                .then(buildMyNetworksCommand())
                .then(buildOpenCommand())
                .then(NetworkOpenCommand.registerOpenAny())
                .then(EnchantSeparateCommand.register())
                .then(ProtectCommand.register())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildNetworkCommands(CommandBuildContext c) {
        return Commands.literal("network")
                .then(NetworkListCommand.register())
                .then(NetworkInfoCommand.register())
                .then(NetworkInsertCommand.register(c))
                .then(NetworkGenerateResourcesCommand.register())
                .then(NetworkToolsCommand.registerGiveTerminal())
                .then(NetworkToolsCommand.registerGiveEnchantedBooks())
                .then(NetworkToolsCommand.registerBatchCreate());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMyNetworksCommand() {
        return NetworkMyNetworksCommand.register();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildOpenCommand() {
        return NetworkOpenCommand.register();
    }
}
