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

    /**
     * 确保 RI 灵魂键 ID 已注册：RI 已注册则跳过；否则注册 BI 的兜底键（同 ID，
     * {@link SoulEnergyStackKey#RI_FALLBACK}），使 RI 被移除后已迁移到该 ID 的既有数据仍可读取。
     * <p>必须在所有模组 commonSetup 结束后调用（{@code FMLLoadCompleteEvent}），
     * 避免与 RI 自身的注册重复。
     */
    public static synchronized void ensureRiKeyFallback() {
        try {
            StackKeyRegistry.getType(SoulEnergyStackKey.RI_FALLBACK_ID);
            return; // RI 已注册
        } catch (IllegalArgumentException absent) {
            // 继续注册兜底键
        } catch (LinkageError unavailable) {
            return;
        }
        try {
            StackKeyRegistry.registerType(SoulEnergyStackKey.RI_FALLBACK);
            SoulDebug.log("registered RI soul fallback key: {}", SoulEnergyStackKey.RI_FALLBACK_ID);
        } catch (IllegalStateException alreadyRegistered) {
            // 竞态下已被注册，忽略
        }
    }
}
