package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 网络独立回蓝（服务端）。
 * <p>启用 {@code irons_mana.independent_regen} 时，对至少有一个在线成员的每个网络，
 * 每 {@link #INTERVAL} tick 固定注入 {@code fixed_regen_amount} 网络蓝。
 */
public class ManaNetRegenHandler {

    private static final int INTERVAL = 10;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!CommandConfig.ironsManaEnabled()) return;
        if (!CommandConfig.ironsManaIndependentRegen()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        if (server.getTickCount() % INTERVAL != 0) return;
        long amount = CommandConfig.ironsManaFixedRegenAmount();
        if (amount <= 0L) return;

        Map<Integer, DimensionsNet> nets = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DimensionsNet net = ManaNetSource.usableNetOf(player);
            if (net != null) nets.putIfAbsent(net.getId(), net);
        }
        for (DimensionsNet net : nets.values()) {
            ManaAccess.insertMana(net, amount);
        }
    }
}
