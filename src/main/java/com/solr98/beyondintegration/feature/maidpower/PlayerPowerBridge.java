package com.solr98.beyondintegration.feature.maidpower;

import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapabilityProvider;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家「P 点」读写桥（1.20.1：TLM 的 {@link PowerCapability}）。
 * <p>把 TLM 的能力读写集中到一处，方便网络拾取/回填与其它模块复用。
 */
public final class PlayerPowerBridge {

    private PlayerPowerBridge() {
    }

    /** 当前 P 点（0 ~ {@link #max()}）。 */
    public static float get(Player player) {
        if (player == null) return 0.0f;
        return player.getCapability(PowerCapabilityProvider.POWER_CAP)
                .map(PowerCapability::get)
                .orElse(0.0f);
    }

    /** P 点上限（TLM 固定 5.0）。 */
    public static float max() {
        return PowerCapability.MAX_POWER;
    }

    /** 增加 P 点（超出上限由 TLM 自行钳制）。 */
    public static void add(Player player, float points) {
        if (player == null || points <= 0.0f) return;
        player.getCapability(PowerCapabilityProvider.POWER_CAP)
                .ifPresent(cap -> cap.add(points));
    }

    /** 直接设置 P 点（0 ~ {@link #max()}）。 */
    public static void set(Player player, float points) {
        if (player == null) return;
        player.getCapability(PowerCapabilityProvider.POWER_CAP)
                .ifPresent(cap -> cap.set(points));
    }
}
