package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class EnchantSeparateCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("enchant")
                .then(Commands.literal("separate")
                        .executes(ctx -> separate(ctx.getSource())));
    }

    private static int separate(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                src.sendFailure(Component.translatable("message.beyond_cmd_extension.no_primary_network"));
                return 0;
            }
            Component result = EnchantmentBookSeparatorHandler.separateAll(net);
            src.sendSuccess(() -> result, false);
        } catch (Exception e) {
            src.sendFailure(Component.translatable("message.beyond_cmd_extension.execute_failed", e.getMessage()));
        }
        return 1;
    }
}
