package com.solr98.beyondintegration.feature.soul;

/**
 * Goety 版本兼容探测（不引用任何版本敏感类，避免旧版 NoClassDefFoundError）。
 */
public final class SoulCompat {

    private static Boolean gainEventAvailable;

    private SoulCompat() {
    }

    /**
     * Goety 是否提供 {@code ChangeSoulEnergyEvent}（≥ 2.5.40.0，2025-12-11 起）。
     * <p>{@code true} → 击杀沉淀走事件处理器（{@code SoulGainHandler}）；
     * {@code false}（旧版）→ 走 {@code SEHelper.increaseSouls} 兜底。
     */
    public static boolean gainEventAvailable() {
        if (gainEventAvailable == null) {
            boolean found;
            try {
                Class.forName("com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent$Gain", false,
                        SoulCompat.class.getClassLoader());
                found = true;
            } catch (Throwable t) {
                found = false;
            }
            gainEventAvailable = found;
        }
        return gainEventAvailable;
    }
}
