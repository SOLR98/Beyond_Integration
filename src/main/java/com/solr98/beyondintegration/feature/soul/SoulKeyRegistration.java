package com.solr98.beyondintegration.feature.soul;

import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;

/**
 * 网络灵魂能量键的注册入口。
 * <p>必须在任何网络存储反序列化之前调用（主类 {@code FMLCommonSetupEvent} 中）。
 */
public final class SoulKeyRegistration {

    private static boolean registered = false;

    private SoulKeyRegistration() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        StackKeyRegistry.registerType(SoulEnergyStackKey.INSTANCE);
        SoulDebug.log("registered soul_energy key: {}", SoulEnergyStackKey.ID);
    }
}
