package com.solr98.beyondintegration.compat.tacz;

import net.minecraft.world.entity.LivingEntity;

/**
 * 射击者上下文（ThreadLocal）：{@code canReload}/{@code hasInventoryAmmo}/{@code consumeAmmoFromPlayer}
 * → {@code findAndExtractInventoryAmmo} 在同一线程内依次调用，用它把"当前射击者"传给
 * 弹药提取逻辑，让背包直读（FeedType.INVENTORY）枪在背包物品弹药不足时从网络补足。
 * <p>放在普通类里（不是 Mixin）：Mixin 之间不能直接访问彼此的 {@code @Unique} 成员。
 */
public final class TaczAmmoShooterContext {

    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();

    private TaczAmmoShooterContext() {}

    public static void set(LivingEntity shooter) {
        if (shooter == null) CURRENT.remove();
        else CURRENT.set(shooter);
    }

    public static LivingEntity get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
