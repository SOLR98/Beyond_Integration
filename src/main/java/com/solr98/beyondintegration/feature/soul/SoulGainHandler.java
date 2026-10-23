package com.solr98.beyondintegration.feature.soul;

import com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 击杀沉淀来源：玩家获得灵魂能量时（{@link ChangeSoulEnergyEvent.Gain}），
 * <b>先填满原版容器</b>，只把「溢出部分」按 {@code kill_ratio} 转入网络（网络作为额外魂源）。
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
        // 原版优先：先填满原版容器（Arca/图腾），只有溢出的部分才沉淀入网
        long overflow = (long) change - SoulVanillaSource.freeSpace(player);
        if (overflow <= 0L) return;
        long toNet = Math.round(overflow * CommandConfig.soulKillRatio());
        if (toNet <= 0L) return;
        long added = SoulEnergyAccess.insertSouls(net, toNet);
        if (added > 0L) {
            event.setSoulChange((int) Math.max(0L, (long) change - added));
            SoulDebug.log("kill overflow +{} -> net {} (event {} -> {})",
                    added, net.getId(), change, event.getSoulChange());
        }
    }
}
