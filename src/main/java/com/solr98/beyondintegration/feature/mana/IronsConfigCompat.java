package com.solr98.beyondintegration.feature.mana;

import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Iron's Spells 跨版本配置兼容。
 * <p>旧版（如 3.4.0.9）的 {@link ServerConfigs} 尚无
 * {@code CREATIVE_MANA_COST} / {@code MANA_REGEN_MULTIPLIER}，直接引用会在运行时
 * 抛 {@link NoSuchFieldError}。这里用反射读取，缺失时回退到旧版语义：
 * <ul>
 *   <li>创造模式仍消耗魔力（旧版无 “creativeMana” 开关，默认照扣）；</li>
 *   <li>回蓝倍率 1.0（旧版公式不含该倍率）。</li>
 * </ul>
 */
public final class IronsConfigCompat {

    /** 旧版语义：creative 照常支付。 */
    private static final boolean LEGACY_CREATIVE_MANA_COST = true;
    /** 旧版公式不含倍率。 */
    private static final double LEGACY_MANA_REGEN_MULTIPLIER = 1.0D;

    private static Boolean creativeManaCost;
    private static Double manaRegenMultiplier;

    private IronsConfigCompat() {
    }

    /** 创造模式是否需要消耗魔力（新版读配置，旧版回退 true）。 */
    public static boolean creativeManaCost() {
        if (creativeManaCost == null) {
            creativeManaCost = readBoolean("CREATIVE_MANA_COST", LEGACY_CREATIVE_MANA_COST);
        }
        return creativeManaCost;
    }

    /** 全局自然回蓝倍率（新版读配置，旧版回退 1.0）。 */
    public static double manaRegenMultiplier() {
        if (manaRegenMultiplier == null) {
            manaRegenMultiplier = readDouble("MANA_REGEN_MULTIPLIER", LEGACY_MANA_REGEN_MULTIPLIER);
        }
        return manaRegenMultiplier;
    }

    private static boolean readBoolean(String fieldName, boolean fallback) {
        Object value = readValue(fieldName);
        return value instanceof Boolean b ? b : fallback;
    }

    private static double readDouble(String fieldName, double fallback) {
        Object value = readValue(fieldName);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static Object readValue(String fieldName) {
        try {
            var field = ServerConfigs.class.getField(fieldName);
            Object raw = field.get(null);
            if (raw instanceof ModConfigSpec.ConfigValue<?> configValue) {
                return configValue.get();
            }
        } catch (Throwable ignored) {
            // 字段不存在（旧版）或反射失败：使用回退值
        }
        return null;
    }
}
