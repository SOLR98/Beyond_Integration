package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 网络独立回蓝（服务端）。
 * <p>启用 {@code irons_mana.independent_regen} 时，对**至少有一个在线成员**的每个网络，
 * 每 {@link #INTERVAL} tick 固定注入 {@code fixed_regen_amount} 网络蓝——不依赖玩家魔力回复。
 * <p>需 {@code irons_spellbooks} 已加载（由主类门控注册）。
 */
public class ManaNetRegenHandler {

    /** 与铁魔法自然回蓝同频（MANA_REGEN_TICKS = 10）。 */
    private static final int INTERVAL = 10;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!CommandConfig.ironsManaEnabled()) return;
        if (!CommandConfig.ironsManaIndependentRegen()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        if (server.getTickCount() % INTERVAL != 0) return;
        long amount = CommandConfig.ironsManaFixedRegenAmount();
        if (amount <= 0L) return;

        // 仅在线的成员所属网络去重回蓝（有在线成员才回）
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
