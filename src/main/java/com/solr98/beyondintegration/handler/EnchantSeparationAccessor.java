package com.solr98.beyondintegration.handler;

/**
 * 附魔分离开关访问器接口。
 * 由 Mixin 注入到玩家或容器相关类，
 * 用于持久化"附魔分离（enchantment separation）"配置开关状态。
 */
public interface EnchantSeparationAccessor {
    /** 查询是否启用附魔分离。 */
    boolean beyond$isEnchantSeparationEnabled();
    /** 设置附魔分离开关状态。 */
    void beyond$setEnchantSeparationEnabled(boolean enabled);
}
