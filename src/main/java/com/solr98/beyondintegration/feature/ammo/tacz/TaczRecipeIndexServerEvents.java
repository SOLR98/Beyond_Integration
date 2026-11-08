package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.BeyondIntegration;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 服务端：配方数据包同步后预构建 TACZ 配方索引缓存（{@link TaczRecipeIndex}）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public final class TaczRecipeIndexServerEvents {

    private TaczRecipeIndexServerEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TaczRecipeIndex.build(server.getRecipeManager());
        }
    }
}
