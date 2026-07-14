package com.solr98.beyondintegration.maid;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.item.ItemStack;

public class NetTerminalBauble implements IMaidBauble {

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (maid.level().isClientSide()) return;
        if (!CommandConfig.enableTokenSystem()) return;
        if (maid.tickCount % 10 != 0) return;

        int netId = NetedItem.getNetId(baubleItem);
        if (netId < 0) return;

        if (MaidTokenUtil.isTokenInvalid(baubleItem, netId)) {
            MaidTokenUtil.clearBinding(baubleItem);
            MaidNetworkCache.remove(maid.getUUID());
        }
    }
}
