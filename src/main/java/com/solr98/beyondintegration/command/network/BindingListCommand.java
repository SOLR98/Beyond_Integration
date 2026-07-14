package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.OutputFormatter;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class BindingListCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("list")
                .then(Commands.argument("netId", IntegerArgumentType.integer(0))
                        .executes(BindingListCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            ctx.getSource().sendFailure(OutputFormatter.createError("binding.network_not_found", netId));
            return 0;
        }

        NetworkBindingRegistry.NetworkEntries entries = NetworkBindingRegistry.getEntries(netId);
        if (entries == null || entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("binding.no_bindings", netId)).withStyle(ChatFormatting.GOLD), false);
            return 0;
        }

        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        ctx.getSource().sendSuccess(() -> OutputFormatter.createTitle("binding.title", netId, netName, entries.totalBindings()), false);
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");

        if (!entries.blocks.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("binding.blocks")).withStyle(ChatFormatting.GRAY), false);
            for (var e : entries.blocks.entrySet()) {
                String time = sdf.format(new Date(e.getValue().timestamp));
                String display = e.getValue().targetName != null
                        ? "§e" + e.getValue().targetName + "§r §7" + e.getKey().toShortString()
                        : "§e" + e.getKey().toShortString();
                Component hover = Component.literal("§7Block: " + e.getKey().toShortString()
                        + "\n§7Operator: " + e.getValue().operator
                        + "\n§7UUID: " + (e.getValue().operatorUuid != null ? e.getValue().operatorUuid : "-")
                        + "\n§7Time: " + time);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + display + " §7by §b" + e.getValue().operator + " §8[" + time + "]")
                        .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))), false);
            }
        }

        if (!entries.items.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("binding.items")).withStyle(ChatFormatting.GRAY), false);
            for (var e : entries.items.entrySet()) {
                String time = sdf.format(new Date(e.getValue().timestamp));
                String display = "§e" + e.getKey();
                Component hover = Component.literal("§7Item: " + e.getKey()
                        + "\n§7Operator: " + e.getValue().operator
                        + "\n§7UUID: " + (e.getValue().operatorUuid != null ? e.getValue().operatorUuid : "-")
                        + "\n§7Time: " + time);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + display + " §7by §b" + e.getValue().operator + " §8[" + time + "]")
                        .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))), false);
            }
        }

        if (!entries.vehicles.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("binding.vehicles")).withStyle(ChatFormatting.GRAY), false);
            for (var e : entries.vehicles.entrySet()) {
                String time = sdf.format(new Date(e.getValue().timestamp));
                String uuidStr = e.getKey().toString();
                String displayName = e.getValue().targetName != null
                        ? "§e" + e.getValue().targetName
                        : "§e" + uuidStr.substring(0, 8) + "...";
                Component hover = Component.literal("§7Entity: " + displayName
                        + "\n§7UUID: " + uuidStr
                        + "\n§7Type: Vehicle"
                        + "\n§7Operator: " + e.getValue().operator
                        + "\n§7Operator UUID: " + (e.getValue().operatorUuid != null ? e.getValue().operatorUuid : "-")
                        + "\n§7Time: " + time);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + displayName + " §7by §b" + e.getValue().operator + " §8[" + time + "]")
                        .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))), false);
            }
        }

        if (!entries.sentries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(CommandLang.get("binding.sentries")).withStyle(ChatFormatting.GRAY), false);
            for (var e : entries.sentries.entrySet()) {
                String time = sdf.format(new Date(e.getValue().timestamp));
                String display = e.getValue().targetName != null
                        ? "§e" + e.getValue().targetName + "§r §7" + e.getKey().toShortString()
                        : "§e" + e.getKey().toShortString();
                Component hover = Component.literal("§7Sentry: " + e.getKey().toShortString()
                        + "\n§7Name: " + (e.getValue().targetName != null ? e.getValue().targetName : "-")
                        + "\n§7Operator: " + e.getValue().operator
                        + "\n§7UUID: " + (e.getValue().operatorUuid != null ? e.getValue().operatorUuid : "-")
                        + "\n§7Time: " + time);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + display + " §7by §b" + e.getValue().operator + " §8[" + time + "]")
                        .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))), false);
            }
        }

        return Command.SINGLE_SUCCESS;
    }
}
