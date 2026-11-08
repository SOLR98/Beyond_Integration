package com.solr98.beyondintegration.feature.charm;

import dev.shadowsoffire.apotheosis.potion.PotionCharmItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * 隔离神化（Apotheosis）药水护符相关类引用。
 * <p>本类引用 {@link PotionCharmItem}，仅在调用方用 {@code ModList.isLoaded("apotheosis")}
 * 确认神化已加载后才会被类加载/调用，避免未装神化时的 NoClassDefFoundError。
 */
public final class ApothCharmSupport {

    private ApothCharmSupport() {}

    /** 该物品是否为神化药水护符。 */
    public static boolean isPotionCharm(ItemStack stack) {
        return stack.getItem() instanceof PotionCharmItem;
    }

    /** 护符是否记录了有效的单条药水效果。 */
    public static boolean hasEffect(ItemStack stack) {
        return PotionCharmItem.hasEffect(stack);
    }

    /** 读取护符记录的药水效果。 */
    public static MobEffectInstance getEffect(ItemStack stack) {
        return PotionCharmItem.getEffect(stack);
    }

    /** 护符开关（NBT {@code charm_enabled}）是否开启。 */
    public static boolean isEnabled(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("charm_enabled");
    }
}
