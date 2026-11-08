package com.solr98.beyondintegration.feature.maidpower;

import com.github.tartaricacid.touhoulittlemaid.data.PowerAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家「P 点」读写桥（1.21.1：TLM 的 {@link PowerAttachment} 数据组件）。
 * <p>把 TLM 的数据读写集中到一处，方便网络拾取/回填与其它模块复用。
 */
public final class PlayerPowerBridge {

    private PlayerPowerBridge() {
    }

    /** 当前 P 点（0 ~ {@link #max()}）。 */
    public static float get(Player player) {
        if (player == null) return 0.0f;
        return player.getData(PowerAttachment.TYPE).get();
    }

    /** P 点上限（TLM 固定 5.0）。 */
    public static float max() {
        return PowerAttachment.MAX_POWER;
    }

    /** 增加 P 点（不越过上限）。 */
    public static void add(Player player, float points) {
        if (player == null || points <= 0.0f) return;
        PowerAttachment attachment = player.getData(PowerAttachment.TYPE);
        float room = Math.max(0.0f, MAX_ROOM(attachment));
        if (room <= 0.0f) return;
        attachment.add(Math.min(points, room));
    }

    /** 直接设置 P 点（0 ~ {@link #max()}）。 */
    public static void set(Player player, float points) {
        if (player == null) return;
        player.getData(PowerAttachment.TYPE).set(points);
    }

    private static float MAX_ROOM(PowerAttachment attachment) {
        return PowerAttachment.MAX_POWER - attachment.get();
    }
}
