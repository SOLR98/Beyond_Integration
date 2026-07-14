package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WorkstationModeConstants {
    public static final OpenStorageMenuPacket.Type[] MODES = {
        OpenStorageMenuPacket.Type.ANVIL, OpenStorageMenuPacket.Type.CUT,
        OpenStorageMenuPacket.Type.GRIND, OpenStorageMenuPacket.Type.SMITH,
        OpenStorageMenuPacket.Type.CRAFT
    };
    public static final int[] MX = {177, 177, 177, 177, 177};
    public static final int[] MY = {0, 16, 31, 46, 61};
    public static final ItemStack[] ICONS = {
        new ItemStack(Items.ANVIL), new ItemStack(Items.STONECUTTER),
        new ItemStack(Items.GRINDSTONE), new ItemStack(Items.SMITHING_TABLE),
        new ItemStack(Items.CRAFTING_TABLE)
    };
}
