package com.solr98.beyondintegration.jade;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum BlockServerProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String NET_ID_KEY = "bce_net_id";
    private static final String CUSTOM_NAME_KEY = "bce_custom_name";
    private static final String ENCHANT_SEP_KEY = "bce_enchant_sep";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof NetedBlockEntity nbe)) return;
        int netId = nbe.getNetId();
        if (netId < 0) return;

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) return;

        data.putInt(NET_ID_KEY, netId);
        String customName = net.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            data.putString(CUSTOM_NAME_KEY, customName);
        }

        if (net instanceof EnchantSeparationAccessor esa) {
            data.putBoolean(ENCHANT_SEP_KEY, esa.beyond$isEnchantSeparationEnabled());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:bd_server");
    }
}
