package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.NetTickTasks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 网络独立回蓝（BD 网络 tick 任务）。
 * <p>启用 {@code irons_mana.independent_regen} 时，对至少有一个在线成员的每个网络，
 * 每 {@link #INTERVAL} tick 固定注入 {@code fixed_regen_amount} 网络蓝。
 * <p>由 BD 的 {@link NetTickTasks} 按网络调度（每网络每 tick 调用，内部节流），
 * 取代原先的全局 {@code ServerTickEvent} + 玩家遍历。
 */
public final class ManaNetRegenTask {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "mana_net_regen");

    private static final int INTERVAL = 10;

    private static volatile MinecraftServer server;

    private ManaNetRegenTask() {
    }

    /** 由主类在服务器启动/停止时维护（BD 的 tick 任务接口拿不到 server）。 */
    public static void setServer(MinecraftServer value) {
        server = value;
    }

    /** 注册为 BD 网络 tick 任务（幂等；ID 已存在会抛异常，故只调用一次）。 */
    public static void register() {
        NetTickTasks.register(ID, ManaNetRegenTask::tick);
    }

    private static void tick(DimensionsNet net) {
        if (!CommandConfig.ironsManaEnabled() || !CommandConfig.ironsManaIndependentRegen()) return;
        MinecraftServer s = server;
        if (s == null || s.getTickCount() % INTERVAL != 0) return;
        long amount = CommandConfig.ironsManaFixedRegenAmount();
        if (amount <= 0L) return;
        if (!hasOnlineMember(net, s)) return;
        ManaAccess.insertMana(net, amount);
    }

    private static boolean hasOnlineMember(DimensionsNet net, MinecraftServer s) {
        for (UUID id : net.getPlayers()) {
            if (s.getPlayerList().getPlayer(id) != null) return true;
        }
        return false;
    }
}
