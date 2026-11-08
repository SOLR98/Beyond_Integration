package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.BeyondIntegration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/**
 * 客户端：配方同步后预构建 TACZ 配方索引缓存（供客户端镜像按配方材料重算使用）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TaczRecipeIndexClientEvents {

    private TaczRecipeIndexClientEvents() {}

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        TaczRecipeIndex.build(event.getRecipeManager());
    }
}
