package com.solr98.beyondintegration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum BlockClientProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final String NET_ID_KEY = "bce_net_id";
    private static final String CUSTOM_NAME_KEY = "bce_custom_name";
    private static final String ENCHANT_SEP_KEY = "bce_enchant_sep";

    private static final boolean BD_HAS_JADE_PROVIDER = hasBdJadeProvider();

    private static boolean hasBdJadeProvider() {
        try {
            Class.forName("com.wintercogs.beyonddimensions.integration.module.jade.NetedBlockNetworkProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) return;

        if (BD_HAS_JADE_PROVIDER) {
            appendCustomName(tooltip, data);
        } else {
            appendFullInfo(tooltip, data);
        }

        appendEnchantSep(tooltip, data);
    }

    private static void appendCustomName(ITooltip tooltip, CompoundTag data) {
        if (data.contains(CUSTOM_NAME_KEY)) {
            String customName = data.getString(CUSTOM_NAME_KEY);
            tooltip.add(Component.translatable("jade.beyond_integration.network_custom_name", customName));
        }
    }

    private static void appendFullInfo(ITooltip tooltip, CompoundTag data) {
        if (!data.contains(NET_ID_KEY)) return;
        int netId = data.getInt(NET_ID_KEY);
        if (data.contains(CUSTOM_NAME_KEY)) {
            String customName = data.getString(CUSTOM_NAME_KEY);
            tooltip.add(Component.translatable("jade.beyond_integration.network_bound_with_name", netId, customName));
        } else {
            tooltip.add(Component.translatable("jade.beyond_integration.network_bound", netId));
        }
    }

    private static void appendEnchantSep(ITooltip tooltip, CompoundTag data) {
        if (!data.contains(ENCHANT_SEP_KEY)) return;
        boolean sepEnabled = data.getBoolean(ENCHANT_SEP_KEY);
        Component sepText = Component.translatable("jade.beyond_integration.enchant_sep",
                Component.translatable(sepEnabled ? "gui.beyond_integration.enchant_sep.on" : "gui.beyond_integration.enchant_sep.off"));
        tooltip.add(sepText);
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.parse("beyond_integration:bd_client");
    }
}
