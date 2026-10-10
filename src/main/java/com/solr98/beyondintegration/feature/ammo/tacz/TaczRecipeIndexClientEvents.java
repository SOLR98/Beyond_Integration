package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端：配方同步后预构建 TACZ 配方索引缓存（供客户端镜像按配方材料重算使用）。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TaczRecipeIndexClientEvents {

    private TaczRecipeIndexClientEvents() {}

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        TaczRecipeIndex.build(event.getRecipeManager());
    }
}
