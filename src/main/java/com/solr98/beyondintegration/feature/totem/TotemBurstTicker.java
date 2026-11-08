package com.solr98.beyondintegration.feature.totem;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 驱动 {@link TotemBurst} 冲击波环逐 tick 扩散动画的服务端处理器。 */
public class TotemBurstTicker {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TotemBurst.serverTick();
    }
}
