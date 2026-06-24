package com.solr98.beyondintegration.integration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.api.IModIntegration;
import com.solr98.beyondintegration.feature.ammo.ywzj.YwzjVehicleSyncer;
import com.solr98.beyondintegration.feature.ammo.ywzj.YwzjCreativeInsertHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

public final class YwzjIntegration implements IModIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final YwzjIntegration INSTANCE = new YwzjIntegration();

    private YwzjIntegration() {}

    @Override
    public String modId() { return "ywzj_vehicle"; }

    @Override
    public void doRegister() {
        NeoForge.EVENT_BUS.register(new YwzjVehicleSyncer());
        UnifiedStorageBeforeInsertHandler.addHandler(new YwzjCreativeInsertHandler());
        LOGGER.info("[BeyondIntegration] ywzj_vehicle integration registered");
    }

    public static boolean isPresent() { return INSTANCE.isLoaded(); }

    public static void registerIfPresent() { INSTANCE.register(); }
}
