package com.solr98.beyondintegration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 客户端组件提供器：为 BD 网络方块（NetedBlock）显示网络状态提示。
 * 与服务端 BDServerProvider 配对，渲染网络 ID、存活状态以及玩家是否为所有者/管理员。
 */
public enum BDClientProvider implements IBlockComponentProvider {
    INSTANCE;

    /** 本提供器的唯一标识（与服务端 UID 对应）。 */
    private static final ResourceLocation UID = new ResourceLocation("beyond_integration", "bd_network");

    /** 从服务端数据读取网络信息并追加网络状态与权限提示。 */
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("bd_net_id")) return;

        int netId = data.getInt("bd_net_id");
        boolean active = data.getBoolean("bd_net_active");
        boolean isOwner = data.getBoolean("bd_net_owner");
        boolean isManager = data.getBoolean("bd_net_manager");

        Component status = active
                ? Component.translatable("command.beyond_integration.network.info.status.active")
                : Component.translatable("command.beyond_integration.network.info.status.deleted");

        tooltip.add(Component.translatable("jade.beyond_integration.network", netId, status));

        if (isOwner) {
            tooltip.add(Component.translatable("jade.beyond_integration.owner"));
        } else if (isManager) {
            tooltip.add(Component.translatable("jade.beyond_integration.manager"));
        }
    }

    /** 返回本提供器的 UID。 */
    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
