package com.solr98.beyondintegration.feature.ammo.sw;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.NetTickTasks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 通用装备位充电（BD 网络 tick 任务）。
 * <p>由 BD {@link NetTickTasks} 按网络调度：只处理「主网络就是该网络」的在线成员，
 * 等价于原先的全局 {@code ServerTickEvent} + 玩家主网络遍历，但同一网络的成员共享一次调度。
 */
public final class EnergyChargeTask {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "energy_charge");

    private static volatile MinecraftServer server;

    private EnergyChargeTask() {
    }

    public static void setServer(MinecraftServer value) {
        server = value;
    }

    public static void register() {
        NetTickTasks.register(ID, EnergyChargeTask::tick);
    }

    private static void tick(DimensionsNet net) {
        MinecraftServer s = server;
        if (s == null) return;
        if (!CommandConfig.energyAmmoChargeEnabled()) return;
        int interval = CommandConfig.energyAmmoChargeInterval();
        if (interval <= 0) return;

        for (UUID id : net.getPlayers()) {
            ServerPlayer player = s.getPlayerList().getPlayer(id);
            if (player == null || player.tickCount % interval != 0) continue;
            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary == null || primary.getId() != net.getId()) continue;
            EnergyAmmoChargeHandler.chargePlayer(player);
        }
    }
}
