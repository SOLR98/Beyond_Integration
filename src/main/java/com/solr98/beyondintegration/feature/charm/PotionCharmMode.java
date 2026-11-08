package com.solr98.beyondintegration.feature.charm;

import java.util.Locale;

/**
 * 网络药水护符的生效目标（网络级）：仅玩家 / 仅女仆 / 玩家和女仆 / 关闭。
 * <p>顺序即按钮正常点击的循环顺序：PLAYERS → MAIDS → BOTH → OFF → …
 * 持久化为 int（{@link #ordinal()}）。
 */
public enum PotionCharmMode {
    PLAYERS,
    MAIDS,
    BOTH,
    OFF;

    public static final int COUNT = values().length;

    /** 由任意 int 归一化取枚举（越界回绕）。 */
    public static PotionCharmMode of(int ordinal) {
        PotionCharmMode[] v = values();
        return v[Math.floorMod(ordinal, v.length)];
    }

    /** 下一个模式（循环）。 */
    public PotionCharmMode next() {
        return of(ordinal() + 1);
    }

    public boolean affectsPlayers() {
        return this == PLAYERS || this == BOTH;
    }

    public boolean affectsMaids() {
        return this == MAIDS || this == BOTH;
    }

    public boolean isOff() {
        return this == OFF;
    }

    /** 对应的本地化 key。 */
    public String langKey() {
        return "gui.beyond_integration.potion_charm.mode." + name().toLowerCase(Locale.ROOT);
    }
}
