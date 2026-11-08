package com.solr98.beyondintegration.feature.soul;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.SEHelper;
import com.Polarice3.Goety.utils.TotemFinder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 原版（Goety）灵魂容器快照：用于实现「原版优先、网络兜底」。
 * <ul>
 *   <li>消耗：原版魂够则完全不碰网络；不足时先扣光原版，余量由网络补。</li>
 *   <li>吸收：先填满原版容器，溢出部分才沉淀入网。</li>
 * </ul>
 * <p>本类引用 Goety 类，仅在 {@code goety} 加载时由调用方类加载（调用方均已门控）。
 */
public final class SoulVanillaSource {

    private SoulVanillaSource() {
    }

    /** 原版当前魂量（Arca / 图腾；不含网络魂）。 */
    public static long nativeSouls(Player player) {
        if (player == null) return 0L;
        if (SEHelper.getSEActive(player)) {
            return Math.max(0, SEHelper.getSESouls(player));
        }
        ItemStack totem = TotemFinder.FindTotem(player);
        if (totem != null && !totem.isEmpty()) {
            return Math.max(0, ITotem.currentSouls(totem));
        }
        return 0L;
    }

    /** 原版魂量上限（Arca 用 MaxArcaSouls；图腾用其自身上限；无容器返回 0）。 */
    public static long capacity(Player player) {
        if (player == null) return 0L;
        if (SEHelper.getSEActive(player)) {
            return Math.max(0, MainConfig.MaxArcaSouls.get());
        }
        ItemStack totem = TotemFinder.FindTotem(player);
        if (totem != null && !totem.isEmpty()) {
            return Math.max(0, ITotem.maximumSouls(totem));
        }
        return 0L;
    }

    /** 原版容器还能再吸收多少（无容器为 0）。 */
    public static long freeSpace(Player player) {
        long cap = capacity(player);
        if (cap <= 0L) return 0L;
        return Math.max(0L, cap - nativeSouls(player));
    }
}
