package com.solr98.beyondintegration.handler;

import java.util.Map;

/**
 * 创作模式/物品计数访问器接口。
 * 由 Mixin 注入到 TaCZ 相关类，用于读写 TaCZ 枪械的创作模式计数数据，
 * 键为物品/枪械标识，值为对应计数。
 */
public interface TaczCreativeAccessor {
    /** 获取 TaCZ 创作模式计数映射（键为标识，值为计数）。 */
    Map<String, Integer> getTaczCreativeCounts();
    /** 设置 TaCZ 创作模式计数映射。 */
    void setTaczCreativeCounts(Map<String, Integer> counts);
}
