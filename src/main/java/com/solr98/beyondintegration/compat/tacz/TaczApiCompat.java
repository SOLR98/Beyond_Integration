package com.solr98.beyondintegration.compat.tacz;

import net.minecraft.world.item.ItemStack;

/**
 * TACZ API 版本探测（运行时兜底门控）。
 * <p>TACZ 1.1.4-hotfix 没有 {@code IGun.useInventoryAmmo(ItemStack)}（1.1.5 才加入），
 * 依赖该 API 的 Mixin 在 1.1.4 下必须在方法体内直接跳过，否则会抛 {@code NoSuchMethodError}。
 */
public final class TaczApiCompat {

    /** 探测结果：TACZ 是否提供 {@code IGun.useInventoryAmmo(ItemStack)}（1.1.5+）。 */
    private static final boolean HAS_USE_INVENTORY_AMMO = detect();

    private TaczApiCompat() {}

    private static boolean detect() {
        try {
            Class.forName("com.tacz.guns.api.item.IGun")
                    .getMethod("useInventoryAmmo", ItemStack.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 当前 TACZ 是否支持 {@code useInventoryAmmo}（1.1.5+）。 */
    public static boolean supportsUseInventoryAmmo() {
        return HAS_USE_INVENTORY_AMMO;
    }
}
