package com.solr98.beyondintegration.core.sync;

/**
 * 主网络扩展开关 / 元数据标记（{@link NetDataType#EXT_FLAGS} 承载）。
 * <p>用于条件式判断这些开关能否从共享镜像读取。
 */
public enum NetFlag {
    ENCHANT_SEPARATION,
    ENERGY_CHARGE,
    POTION_CHARM_MODE;

    public int bit() {
        return 1 << ordinal();
    }
}
