package com.solr98.beyondintegration.feature.mana;

import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;

/**
 * 网络魔力键的注册入口。
 * <p>必须在任何网络存储反序列化之前调用（主类 {@code FMLCommonSetupEvent} 中）；幂等。
 * <p>同时注册旧 ID（{@link ManaStackKey#LEGACY}），使旧存档中 {@code stack_type/mana} 仍可反序列化，
 * 之后由 {@code ManaKeyMigration} 迁移到新 ID {@code stack_type/iron_mana}。
 */
public final class ManaKeyRegistration {

    private static boolean registered = false;

    private ManaKeyRegistration() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        StackKeyRegistry.registerType(ManaStackKey.INSTANCE);
        StackKeyRegistry.registerType(ManaStackKey.LEGACY);
    }
}
