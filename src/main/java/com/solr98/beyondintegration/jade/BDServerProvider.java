package com.solr98.beyondintegration.jade;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade 服务端数据提供器：为 BD 的 NetedBlockEntity（网络方块实体）补充网络信息。
 * 写入网络 ID、网络存活状态及当前玩家是否为网络所有者/管理员等权限数据。
 */
public enum BDServerProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    /** 本提供器的唯一标识。 */
    private static final ResourceLocation UID = new ResourceLocation("beyond_integration", "bd_network");

    /** 将方块所属网络的 ID、存活状态及玩家权限写入服务端数据标签。 */
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

    /** 返回本提供器的 UID，用于与客户端提供器配对。 */
    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
