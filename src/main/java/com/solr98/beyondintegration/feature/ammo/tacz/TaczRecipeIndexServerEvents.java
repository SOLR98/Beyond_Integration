package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 服务端：配方数据包同步后预构建 TACZ 配方索引缓存（{@link TaczRecipeIndex}）。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
