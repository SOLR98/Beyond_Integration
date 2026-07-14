package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;
import net.minecraft.commands.CommandSourceStack;

public final class NetworkPermission {

    private NetworkPermission() {}

    public static boolean isOpLevel(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }

    public static boolean isOpOrHost(CommandSourceStack source) {
        return source.hasPermission(2) || source.getServer().isSingleplayer();
    }

    public static int getRequiredOpLevel() {
        return 2;
    }

    public static void sendNoPermission(CommandSourceStack source) {
        source.sendFailure(net.minecraft.network.chat.Component.translatable(
                "commands.beyond_integration.no_permission"));
    }
}
