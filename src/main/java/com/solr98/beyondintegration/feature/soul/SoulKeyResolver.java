package com.solr98.beyondintegration.feature.soul;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 灵魂存储键解析：默认用 BI 自己的 {@link SoulEnergyStackKey}；
 * 开启 {@code goety_soul.use_ri_key} 且 RI 已注册其键时，改用
 * {@code beyonddimensions:stack_type/goety_soul}（RI 的 canonical 键）。
 */
public final class SoulKeyResolver {

    /** RI（rs_integration）注册到 BD 的灵魂键。 */
    public static final ResourceLocation RI_SOUL_ID = SoulEnergyStackKey.RI_FALLBACK_ID;

    private SoulKeyResolver() {
    }

    /** 当前用于读写的灵魂键（配置开启且 RI 键可用时用 RI 键，否则回退 BI 键）。 */
    public static IStackKey<?> current() {
        if (useRiKey()) {
            IStackKey<?> ri = riKey();
            if (ri != null) return ri;
        }
        return SoulEnergyStackKey.INSTANCE;
    }

    /**
     * 是否启用 RI 键：服务端读本地配置；客户端优先采用 {@code SyncConfigSyncPayload}
     * 下发的服务端权威值（未下发时回退本地配置）。
     */
    public static boolean useRiKey() {
        boolean configured;
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            configured = com.solr98.beyondintegration.client.SyncConfigClient
                    .getBoolean("goety_soul.use_ri_key", CommandConfig.soulUseRiKey());
        } else {
            configured = CommandConfig.soulUseRiKey();
        }
        // RI 不存在或其键尚未注册时，本配置无效，自动回退 BI 键
        return configured && riKey() != null;
    }
    /** RI 灵魂键实例；未注册/不可用时返回 null。 */
    public static @Nullable IStackKey<?> riKey() {
        if (RI_SOUL_ID == null) return null;
        try {
            return StackKeyRegistry.getType(RI_SOUL_ID);
        } catch (IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    /** 该键是否应视为“灵魂”（BI 键或 RI 键；供 HUD/类型同步判定）。 */
    public static boolean isSoulKey(IStackKey<?> key) {
        if (key == null) return false;
        return key instanceof SoulEnergyStackKey || RI_SOUL_ID.equals(key.getTypeId());
    }
}
