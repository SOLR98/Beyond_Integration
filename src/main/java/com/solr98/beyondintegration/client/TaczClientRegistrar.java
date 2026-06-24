package com.solr98.beyondintegration.client;

import com.tacz.guns.api.event.common.GunDrawEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;

public class TaczClientRegistrar {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(GunDrawEvent.class, e -> {
            if (e.getLogicalSide() == LogicalSide.CLIENT
                    && e.getEntity() instanceof net.minecraft.client.player.LocalPlayer) {
                ResourceLocation ammoId =
                        com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor.getAmmoIdClient(e.getCurrentGunItem());
                if (ammoId != null)
                    TaczAmmoCache.requestQuick(ammoId);
            }
        });
    }
}
