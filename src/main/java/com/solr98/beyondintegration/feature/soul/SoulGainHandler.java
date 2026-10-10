package com.solr98.beyondintegration.feature.soul;

import com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 击杀沉淀来源：玩家获得灵魂能量时（{@link ChangeSoulEnergyEvent.Gain}），
 * 按 {@code kill_ratio} 把其中一部分转化为网络灵魂（网络作为额外魂源）。
 * <p>仅在该模块启用且 {@code source_kill=true} 时生效；默认关。
 * <p>仅 1.20.1，且类仅在 {@code goety} 加载时注册（类体引用 Goety 事件）。
 */
public class SoulGainHandler {

    @SubscribeEvent
    public void onGain(ChangeSoulEnergyEvent.Gain event) {
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulSourceKill()) return;
        Player player = event.getEntity();
        int change = event.getSoulChange();
        if (change <= 0) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        long toNet = Math.round(change * CommandConfig.soulKillRatio());
        if (toNet <= 0L) return;
        long added = SoulEnergyAccess.insertSouls(net, toNet);
        if (added > 0L) {
            event.setSoulChange((int) Math.max(0L, (long) change - added));
            SoulDebug.log("kill precipitate +{} -> net {} (event {} -> {})",
                    added, net.getId(), change, event.getSoulChange());
        }
    }
}
