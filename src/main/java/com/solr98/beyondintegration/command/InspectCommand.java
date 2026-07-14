package com.solr98.beyondintegration.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.handler.PlayerInspectData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class InspectCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("inspect")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        PlayerInspectData.toggle(player.getUUID());
                        boolean enabled = PlayerInspectData.isInspectMode(player.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                enabled ? "§a[Audit Mode] ON — Click blocks to view audit logs"
                                        : "§7[Audit Mode] OFF")
                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
                        return Command.SINGLE_SUCCESS;
                    }
                    ctx.getSource().sendFailure(Component.literal("Player only"));
                    return 0;
                });
    }
}
