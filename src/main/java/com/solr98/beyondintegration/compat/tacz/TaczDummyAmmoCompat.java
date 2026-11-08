package com.solr98.beyondintegration.compat.tacz;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * TACZ 1.2.0 虚拟备弹（{@code DummyAmmo}）API 的反射兼容层。
 * <p>1.2.0 新增 {@code IGun.useDummyAmmo/getDummyAmmoAmount}（1.1.x 编译期不存在），
 * 这里用反射探测并缓存，供各 Mixin 共用（避免在 Mixin 类里放非 private 静态方法导致注入失败）。
 */
public final class TaczDummyAmmoCompat {

    private static final Method USE_DUMMY_AMMO = find("useDummyAmmo");
    private static final Method GET_DUMMY_AMOUNT = find("getDummyAmmoAmount");

    private TaczDummyAmmoCompat() {}

    /** 枪是否使用 1.2.0 的虚拟备弹；旧版（无该 API）返回 false。 */
    public static boolean useDummyAmmo(IGun iGun, ItemStack gun) {
        if (USE_DUMMY_AMMO == null || iGun == null || gun == null) return false;
        try {
            return Boolean.TRUE.equals(USE_DUMMY_AMMO.invoke(iGun, gun));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 枪的虚拟备弹余量；旧版（无该 API）返回 -1。 */
    public static int dummyAmmoAmount(IGun iGun, ItemStack gun) {
        if (GET_DUMMY_AMOUNT == null || iGun == null || gun == null) return -1;
        try {
            Object value = GET_DUMMY_AMOUNT.invoke(iGun, gun);
            return value instanceof Integer amount ? amount : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Method find(String name) {
        try {
            return Class.forName("com.tacz.guns.api.item.IGun").getMethod(name, ItemStack.class);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
