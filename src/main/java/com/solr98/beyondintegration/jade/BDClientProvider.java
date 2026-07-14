package com.solr98.beyondintegration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum BDClientProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("beyond_integration", "bd_network");

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

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
