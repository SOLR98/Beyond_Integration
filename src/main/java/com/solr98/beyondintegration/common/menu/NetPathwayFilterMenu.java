package com.solr98.beyondintegration.common.menu;

import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * 维度网络通道（net_pathway）过滤界面（1.21.1）。
 */
public class NetPathwayFilterMenu extends BDBaseMenu {

    private static final int slotStartY = 1 + CommonTextures.TOP_BASE_COMMON_HEIGHT;

    public final StackHandler filterSlots;
    public final NetPathwayFilterAccess access;

    public NetPathwayFilterMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, resolve(inventory, data));
    }

    private static NetPathwayFilterAccess resolve(Inventory inventory, FriendlyByteBuf data) {
        var pos = data.readBlockPos();
        var be = inventory.player.level().getBlockEntity(pos);
        return (be instanceof NetPathwayFilterAccess access) ? access : null;
    }

    public NetPathwayFilterMenu(int id, Inventory inventory, NetPathwayFilterAccess access) {
        super(ModMenus.NET_PATHWAY_FILTER.get(), id, inventory);
        this.access = access;
        this.filterSlots = access == null ? new StackHandler(0) : access.beyond$getFilterSlots();

        int slots = filterSlots.getSlots();
        for (int i = 0; i < slots; i++) {
            int col = i % 9;
            int row = i / 9;
            addSlot(new FlagStackTypedSlot(this, filterSlots, i, 8 + col * 18, slotStartY + row * 18));
        }

        int invTop = slotStartY + Math.max(1, (slots + 8) / 9) * 18
                + CommonTextures.COMMON_CONNECTION_HEIGHT + 6;
        inventoryStartIndex = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, invTop + 58));
        }
        inventoryEndIndex = this.slots.size();
    }

    @Override
    protected boolean shouldSendQuickData() {
        return false;
    }

    @Override
    protected void writeQuickDataTag(CompoundTag tag) {
        super.writeQuickDataTag(tag);
        if (access != null) {
            tag.putBoolean("beyond_filter_enabled", access.beyond$isFilterEnabled());
            tag.putBoolean("beyond_only_input", access.beyond$isOnlyInput());
            tag.putBoolean("beyond_fuzzy", access.beyond$isFuzzy());
        }
    }

    @Override
    public void readQuickDataTag(CompoundTag tag) {
        super.readQuickDataTag(tag);
        if (access == null) return;
        access.beyond$setFilterEnabled(tag.getBoolean("beyond_filter_enabled"));
        access.beyond$setOnlyInput(tag.getBoolean("beyond_only_input"));
        access.beyond$setFuzzy(tag.getBoolean("beyond_fuzzy"));
    }

    @Override
    public boolean stillValid(Player player) {
        return access instanceof net.minecraft.world.level.block.entity.BlockEntity be && !be.isRemoved();
    }
}
