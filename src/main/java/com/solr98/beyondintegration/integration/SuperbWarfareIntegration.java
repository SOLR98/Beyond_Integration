package com.solr98.beyondintegration.integration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.api.IModIntegration;
import com.solr98.beyondintegration.feature.ammo.sw.SwPlayerAmmoSyncer;
import com.solr98.beyondintegration.feature.ammo.sw.SwVehicleAmmoSyncer;
import com.solr98.beyondintegration.feature.ammo.sw.SuperbAmmoInsertHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

public final class SuperbWarfareIntegration implements IModIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SuperbWarfareIntegration INSTANCE = new SuperbWarfareIntegration();

    private SuperbWarfareIntegration() {}

    @Override
    public String modId() { return "superbwarfare"; }

    @Override
    public void doRegister() {
        UnifiedStorageBeforeInsertHandler.addHandler(new SuperbAmmoInsertHandler());
        NeoForge.EVENT_BUS.register(new SwPlayerAmmoSyncer());
        NeoForge.EVENT_BUS.register(new SwVehicleAmmoSyncer());
        LOGGER.info("[BeyondIntegration] SuperbWarfare integration registered");
    }

    public static boolean isPresent() { return INSTANCE.isLoaded(); }

    public static void registerIfPresent() { INSTANCE.register(); }
}
