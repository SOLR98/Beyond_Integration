package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public enum NetworkPermission {
    NONE("none", "network.info.no_permission", ChatFormatting.GRAY),
    MEMBER("member", "network.myNetworks.permission.member", ChatFormatting.GREEN),
    MANAGER("manager", "network.myNetworks.permission.manager", ChatFormatting.BLUE),
    OWNER("owner", "network.myNetworks.permission.owner", ChatFormatting.RED);

    private final String key;
    private final String displayKey;
    private final ChatFormatting color;

    NetworkPermission(String key, String displayKey, ChatFormatting color) {
        this.key = key;
        this.displayKey = displayKey;
        this.color = color;
    }

    public String getKey() { return key; }
    public String getDisplay() { return CommandLang.get(displayKey); }
    public ChatFormatting getColor() { return color; }

    public static NetworkPermission fromPlayer(ServerPlayer player, DimensionsNet net) {
        if (net == null || player == null) return NONE;
        UUID id = player.getUUID();
        if (net.isOwner(id)) return OWNER;
        if (net.isManager(id)) return MANAGER;
        if (net.getPlayers().contains(id)) return MEMBER;
        return NONE;
    }
}
