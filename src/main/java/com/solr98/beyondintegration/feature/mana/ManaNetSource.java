package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;

/**
 * 网络魔力源解析：把"某玩家的主网络"作为额外魔力源。
 * <p>仅当联动启用且玩家存在主网络时可用，否则返回 {@code null}（行为同现状）。
 */
public final class ManaNetSource {

    private ManaNetSource() {
    }

    /** 玩家的主网络（需联动启用）；不满足返回 {@code null}。 */
    public static DimensionsNet usableNetOf(Player player) {
        if (player == null) return null;
        if (!CommandConfig.ironsManaEnabled()) return null;
        return DimensionsNet.getPrimaryNetFromPlayer(player);
    }
}
