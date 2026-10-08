package com.solr98.beyondintegration.feature.magnet;

/**
 * 网络磁铁的自定义吸取档位：名称 + 半径（{@code -1} 表示整个区块）+ 执行间隔（tick）。
 * <p>物品吸取与流体吸取各自维护一套档位，互不影响。
 */
public record MagnetTier(String name, int radius, int interval) {

    /** 是否为“整区块”模式（半径 {@code < 0}）。 */
    public boolean chunk() {
        return radius < 0;
    }
}
