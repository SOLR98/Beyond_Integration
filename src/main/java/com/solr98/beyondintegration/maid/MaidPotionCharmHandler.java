package com.solr98.beyondintegration.maid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.charm.NetworkPotionCharmHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 女仆网络药水护符监听。
 * <p>订阅 TLM 女仆每 tick 事件（{@link MaidTickEvent}），按配置间隔节流后，
 * 解析女仆绑定的维度网络（{@link MaidNetworkHelper#findTerminal}），
 * 把该网络内护符的效果施加到女仆身上（消耗规则与玩家一致：无法破坏不消耗、经验修补消耗网络 XP）。
 * <p>仅当 touhou_little_maid 加载时由主类注册本监听器。
 */
public class MaidPotionCharmHandler {

    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide) return;
        if (!CommandConfig.potionCharmEnabled()) return;
        int interval = CommandConfig.potionCharmInterval();
        if (interval <= 0 || maid.tickCount % interval != 0) return;

        DimensionsNet net = MaidNetworkHelper.findTerminal(maid);
        if (net == null) return;
        if (!com.solr98.beyondintegration.feature.charm.PotionCharmMode
                .of(NetworkPotionCharmHandler.getMode(net)).affectsMaids()) return;
        NetworkPotionCharmHandler.applyToEntity(net, maid);
    }
}
