package com.solr98.beyondintegration.feature.charm;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.NetTickTasks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 网络药水护符（BD 网络 tick 任务）。
 * <p>由 BD {@link NetTickTasks} 按网络调度：只处理「主网络就是该网络」的在线成员，
 * 护符记录按网络缓存，同一网络多成员共享一次扫描。
 */
public final class PotionCharmTask {

    public static final ResourceLocation ID =
            new ResourceLocation("beyond_integration", "potion_charm");

    private static volatile MinecraftServer server;

    private PotionCharmTask() {
    }

    public static void setServer(MinecraftServer value) {
        server = value;
    }

    public static void register() {
        NetTickTasks.register(ID, PotionCharmTask::tick);
    }

    private static void tick(DimensionsNet net) {
        MinecraftServer s = server;
        if (s == null) return;
        if (!CommandConfig.potionCharmEnabled()) return;
        int interval = CommandConfig.potionCharmInterval();
        if (interval <= 0) return;
        if (!PotionCharmMode.of(NetworkAmmoData.getOrCreate(net.getId()).getPotionCharmMode()).affectsPlayers()) return;

        for (UUID id : net.getPlayers()) {
            ServerPlayer player = s.getPlayerList().getPlayer(id);
            if (player == null || player.tickCount % interval != 0) continue;
            DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (primary == null || primary.getId() != net.getId()) continue;
            NetworkPotionCharmHandler.applyToEntity(net, player);
        }
    }
}
