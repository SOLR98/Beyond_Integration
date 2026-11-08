package com.solr98.beyondintegration.handler;

/**
 * 网络药水护符"生效目标"访问器接口。
 * 由 DimensionsNetMixin 注入到 DimensionsNet，用于持久化网络级目标模式
 * （仅玩家 / 仅女仆 / 玩家和女仆 / 关闭，见 {@code PotionCharmMode} 的序号）。
 */
public interface PotionCharmAccessor {
    /** 获取网络药水护符生效目标（PotionCharmMode 序号）。 */
    int beyond$getPotionCharmMode();
    /** 设置网络药水护符生效目标。 */
    void beyond$setPotionCharmMode(int mode);
}
