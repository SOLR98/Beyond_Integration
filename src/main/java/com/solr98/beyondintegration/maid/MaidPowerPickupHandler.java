package com.solr98.beyondintegration.maid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidPickupEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.maidpower.MaidPowerAccess;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 女仆拾取 P 点时把点数存入其绑定网络（而不是按 TLM 原逻辑转成经验/修补）。
 * <p>仅当 {@code maid_power.enabled} 与 {@code maid_power.pickup_to_network} 开启、
 * 且网络能完整接收时拦截（取消事件 + 丢弃 P 点实体）；否则交回 TLM 原逻辑。
 */
public class MaidPowerPickupHandler {

    @SubscribeEvent
    public void onPowerPointPickup(MaidPickupEvent.PowerPointResult event) {
        if (event.isSimulate()) return;
        if (!CommandConfig.maidPowerEnabled() || !CommandConfig.maidPowerPickupToNetwork()) return;

        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide()) return;

        EntityPowerPoint powerPoint = event.getPowerPoint();
        if (powerPoint == null || !powerPoint.isAlive()) return;

        long value = powerPoint.getValue();
        if (value <= 0L) return;

        DimensionsNet net = MaidNetworkHelper.findTerminal(maid);
        if (net == null) return;

        long leftover = MaidPowerAccess.insert(net, value);
        if (leftover > 0L) {
            // 网络容量不足：已存入的部分保留，其余交回 TLM 原逻辑处理
            if (leftover >= value) return;
            powerPoint.value = (int) Math.min(Integer.MAX_VALUE, leftover);
        } else {
            powerPoint.discard();
        }
        net.setDirty();
        event.setCanceled(true);
    }
}
