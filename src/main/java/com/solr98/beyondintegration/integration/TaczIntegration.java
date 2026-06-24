package com.solr98.beyondintegration.integration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.api.IModIntegration;
import com.solr98.beyondintegration.feature.ammo.tacz.AmmoBoxExtractHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import org.slf4j.Logger;

public final class TaczIntegration implements IModIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TaczIntegration INSTANCE = new TaczIntegration();

    private TaczIntegration() {}

    @Override
    public String modId() { return "tacz"; }

    @Override
    public void doRegister() {
        UnifiedStorageBeforeInsertHandler.addHandler(new AmmoBoxExtractHandler());
        LOGGER.info("[BeyondIntegration] TaCZ integration registered");
    }

    public static boolean isPresent() { return INSTANCE.isLoaded(); }

    public static void registerIfPresent() { INSTANCE.register(); }
}
