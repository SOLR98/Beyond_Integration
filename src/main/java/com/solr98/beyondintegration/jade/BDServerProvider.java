package com.solr98.beyondintegration.jade;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum BDServerProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("beyond_integration", "bd_network");

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof NetedBlockEntity neted)) return;

        int netId = neted.getNetId();
        if (netId < 0) return;

        DimensionsNet net = neted.getNet();
        if (net == null) return;

        tag.putInt("bd_net_id", netId);
        tag.putBoolean("bd_net_active", !net.deleted);

        if (accessor.getPlayer() instanceof ServerPlayer player) {
            tag.putBoolean("bd_net_owner", net.isOwner(player));
            tag.putBoolean("bd_net_manager", net.isManager(player));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
