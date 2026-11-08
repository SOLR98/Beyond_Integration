package com.solr98.beyondintegration.integration.ae2lt.compat;

import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.solr98.beyondintegration.integration.ae2lt.storage.LightningStackKey;
import com.solr98.beyondintegration.integration.ae2lt.storage.LightningType;
import com.wintercogs.beyonddimensions.integration.module.ae2.AEHelper;

import java.util.Optional;

/**
 * 「维度 AE 存储磁盘」（BD 的 {@code NetAEStorageCell}）闪电桥接。
 * <p>
 * BD 的 AE 存储磁盘通过 {@link AEHelper} 的两张转换表，把 AE2 的 {@code AEKey} 与维度网络的
 * {@code IStackKey} 互转。在此登记闪电的映射后，磁盘即可在 AE 网络与维度网络之间读写闪电：
 * <ul>
 *   <li>维度网络闪电（{@link LightningStackKey}）↔ AE2LT 闪电键（{@link LightningKey}）</li>
 * </ul>
 * 仅在 AE2 已加载（即 BD 的 AE 集成模块激活、{@code AEHelper} 可用）时调用。
 */
public final class Ae2ltAeStorageBridge {

    private Ae2ltAeStorageBridge() {
    }

    /** 注册闪电的 AE ⇄ 维度网络转换映射（幂等：重复 put 覆盖同键）。 */
    public static void register() {
        // 维度网络 -> AE：LightningStackKey(ID) => LightningKey（按档位）
        AEHelper.ISTACK_TO_AEKEY_MAP.put(LightningStackKey.ID,
                key -> Optional.of(LightningKey.of(((LightningType) key.copyStack()).tier())));
        // AE -> 维度网络：AE2LT 闪电键类型 => LightningStackKey（按档位）
        AEHelper.AEKEY_TO_STACK_TYPE_MAP.put(LightningKeyType.INSTANCE,
                aeKey -> Optional.of(LightningStackKey.of(((LightningKey) aeKey).apiTier())));
    }
}
