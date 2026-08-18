package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 工作站模式常量：定义 5 种工作站模式对应的网络包类型、
 * 左侧图标按钮的坐标（MX/MY）与物品图标（ICONS），三者一一对应。
 */
public class WorkstationModeConstants {
    /** 五种工作站模式的网络包类型（铁砧/切割/磨石/锻造/合成） */
    public static final OpenStorageMenuPacket.Type[] MODES = {
        OpenStorageMenuPacket.Type.ANVIL, OpenStorageMenuPacket.Type.CUT,
        OpenStorageMenuPacket.Type.GRIND, OpenStorageMenuPacket.Type.SMITH,
        OpenStorageMenuPacket.Type.CRAFT
    };
    /** 图标按钮 X 坐标（统一为 177） */
    public static final int[] MX = {177, 177, 177, 177, 177};
    /** 图标按钮 Y 坐标（自上而下每行间隔 15px） */
    public static final int[] MY = {0, 16, 31, 46, 61};
    /** 对应工作站的物品图标（铁砧/切石机/磨石/锻造台/工作台） */
    public static final ItemStack[] ICONS = {
        new ItemStack(Items.ANVIL), new ItemStack(Items.STONECUTTER),
        new ItemStack(Items.GRINDSTONE), new ItemStack(Items.SMITHING_TABLE),
        new ItemStack(Items.CRAFTING_TABLE)
    };
}
