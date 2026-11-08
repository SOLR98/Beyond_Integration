package com.solr98.beyondintegration.compat.tud;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tacz-Unidict（TUD）兼容层——1.21.1 空桩。
 *
 * <p>TUD 尚无 1.21.1 版本，故本分支不做编译期依赖，所有查询直接返回 {@code null}／{@code false}，
 * 调用方（{@code TaczAmmoMirror} 等）自动回退 TACZ 原版弹药路径。
 *
 * <p>若日后 TUD 移植到 1.21.1，只需把 {@link #ENABLED} 打开并补回
 * {@code AmmoManager}/{@code AmmoData}/{@code CommonConfig} 的实现即可，调用点无需改动。
 */
public final class TudAmmoCompat {

    /** TUD 在 1.21.1 不存在，恒为 false */
    private static final boolean ENABLED = false;

    private TudAmmoCompat() {}

    /** TUD 是否可用（1.21.1 恒 false） */
    public static boolean isLoaded() {
        return ENABLED;
    }

    /** 解析枪的 TUD 当前弹药 ID：1.21.1 恒返回 null，调用方回退 TACZ 原版 */
    @Nullable
    public static ResourceLocation resolve(ItemStack gun) {
        return null;
    }

    /** 存储物品是否为 TUD 物品弹药：1.21.1 恒返回 null */
    @Nullable
    public static ResourceLocation getItemAmmoId(ItemStack stack) {
        return null;
    }

    /** 按弹药 ID 构造物品弹药参考堆：1.21.1 恒返回 null */
    @Nullable
    public static ItemStack getItemAmmoStack(ResourceLocation ammoId) {
        return null;
    }
}
