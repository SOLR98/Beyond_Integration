package com.solr98.beyondintegration.feature.soul;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import org.slf4j.Logger;

/** 网络灵魂源调试日志（仅 {@code goety_soul.debug=true} 时输出）。 */
public final class SoulDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SoulDebug() {
    }

    public static boolean on() {
        try {
            return CommandConfig.soulDebug();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void log(String msg, Object... args) {
        if (on()) LOGGER.info("[BI-Soul] " + msg, args);
    }
}
