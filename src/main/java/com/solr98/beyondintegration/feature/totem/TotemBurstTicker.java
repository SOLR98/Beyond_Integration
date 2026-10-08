package com.solr98.beyondintegration.feature.totem;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 驱动 {@link TotemBurst} 冲击波环逐 tick 扩散动画的服务端处理器。 */
public class TotemBurstTicker {

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TotemBurst.serverTick();
    }
}
