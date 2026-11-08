package com.solr98.beyondintegration.maid;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.maidpower.MaidPowerAccess;
import com.solr98.beyondintegration.feature.maidpower.PlayerPowerBridge;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 玩家 P 点自动回填：玩家 P 点未满且当前网络有余额时，按配置间隔与单次上限从网络补足。
 * <p>使「女仆拾取入网的 P 点」可直接被玩家消耗（祭坛/信标等 TLM 功能会自动从网络回补）。
 */
public class MaidPowerRefillHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!CommandConfig.maidPowerEnabled() || !CommandConfig.maidPowerAutoRefill()) return;

        int interval = CommandConfig.maidPowerRefillInterval();
        if (interval <= 0 || player.tickCount % interval != 0) return;

        float current = PlayerPowerBridge.get(player);
        float max = PlayerPowerBridge.max();
        if (current >= max) return;

        DimensionsNet net = NetMenuResolver.resolve(player);
        if (net == null) return;

        long needUnits = MaidPowerAccess.toUnits(max - current);
        long capUnits = MaidPowerAccess.toUnits(CommandConfig.maidPowerRefillMaxPoints());
        long take = Math.min(needUnits, capUnits);
        if (take <= 0L) return;

        long got = MaidPowerAccess.extract(net, take);
        if (got <= 0L) return;

        PlayerPowerBridge.add(player, MaidPowerAccess.toPoints(got));
        net.setDirty();
    }
}
