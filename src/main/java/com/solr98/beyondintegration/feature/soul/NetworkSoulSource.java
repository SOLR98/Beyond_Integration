package com.solr98.beyondintegration.feature.soul;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;

/**
 * 网络灵魂源解析：把"某玩家的主网络"作为额外魂源。
 * <p>仅在模块启用、{@code direct_main_net=true}、且该网络已献祭激活时可用（默认全关，行为同现状）。
 */
public final class NetworkSoulSource {

    private NetworkSoulSource() {
    }

    /** 玩家可用于仪式/牢笼的主网络（需 {@code direct_main_net}）；不满足返回 {@code null}。 */
    public static DimensionsNet mainNetOf(Player player) {
        return CommandConfig.soulDirectMainNet() ? usableNetOf(player) : null;
    }

    /** 玩家的主网络（仅需"启用 + 已献祭激活"）；用于来源沉淀/玩家侧并入，不受 {@code direct_main_net} 限制。 */
    public static DimensionsNet usableNetOf(Player player) {
        if (player == null) return null;
        if (!CommandConfig.soulEnabled()) return null;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        return usable(net) ? net : null;
    }

    /** 网络是否可作为灵魂源（启用 + 已献祭激活）。 */
    public static boolean usable(DimensionsNet net) {
        return net != null && CommandConfig.soulEnabled() && SoulEnergyAccess.isActivated(net);
    }
}
