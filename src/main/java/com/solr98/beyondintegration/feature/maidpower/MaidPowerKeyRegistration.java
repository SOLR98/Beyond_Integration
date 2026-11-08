package com.solr98.beyondintegration.feature.maidpower;

import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;

/**
 * 网络「女仆 P 点」键的注册入口。
 * <p>必须在任何网络存储反序列化之前调用（主类 {@code FMLCommonSetupEvent} 中）。
 */
public final class MaidPowerKeyRegistration {

    private static boolean registered = false;

    private MaidPowerKeyRegistration() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        StackKeyRegistry.registerType(MaidPowerStackKey.INSTANCE);
    }
}
