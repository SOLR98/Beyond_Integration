package com.solr98.beyondintegration.integration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.api.IModIntegration;
import org.slf4j.Logger;

public final class TaczAddonIntegration implements IModIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TaczAddonIntegration INSTANCE = new TaczAddonIntegration();

    private TaczAddonIntegration() {}

    @Override
    public String modId() {
        return "taczaddon";
    }

    @Override
    public void doRegister() {
        LOGGER.info("[BeyondIntegration] TaCZ Addon integration registered");
    }

    public static boolean isPresent() {
        return INSTANCE.isLoaded();
    }

    public static void registerIfPresent() {
        INSTANCE.register();
    }
}
