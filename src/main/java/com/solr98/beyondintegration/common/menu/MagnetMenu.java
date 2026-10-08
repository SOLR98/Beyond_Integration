package com.solr98.beyondintegration.common.menu;

import com.solr98.beyondintegration.feature.magnet.MagnetSettings;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.common.init.BDItems;
import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.machine.FilterMode;
import com.wintercogs.beyonddimensions.common.machine.HopperFluidMode;
import com.wintercogs.beyonddimensions.common.machine.HopperItemMode;
import com.wintercogs.beyonddimensions.common.machine.HopperNBTMode;
import com.wintercogs.beyonddimensions.common.machine.HopperRangeMode;
import com.wintercogs.beyonddimensions.common.machine.HopperXpMode;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import com.wintercogs.beyonddimensions.util.InventoryHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络磁铁菜单的 BI 版实现（从 BD {@code NetMagnetMenu} 复制而来，改为注册到 BI 菜单类型
 * {@code beyond_integration:magnet}）。用于让 BI 自定义界面（{@code MagnetGUI}）与 BD 原菜单等价地
 * 承载磁铁设置与标记槽，同时保持 QuickData 同步逻辑一致。
 */
public class MagnetMenu extends BDBaseMenu {

    private static final int slotStartY = CommonTextures.TOP_BASE_COMMON_HEIGHT + 1;
    private static final int invSlotStartY = CommonTextures.TOP_BASE_COMMON_HEIGHT
            + CommonTextures.FILTER_SLOTS_HEIGHT * 4 + CommonTextures.COMMON_CONNECTION_HEIGHT + 7;

    private final IStackHandler storage = new StackHandler(36) {
        @Override
        public void onChange() {
            super.onChange();
            if (!player.level().isClientSide() && initialized) {
                BaseMachineItem.setFilterSlots(menuStack, new ArrayList<>(storage.getStorage()));
            }
        }
    };
    private boolean initialized;

    public final ItemStack menuStack;

    private RedStoneControlMode lastControlMode;
    private FilterMode lastFilterMode;
    private HopperItemMode lastHopperItemMode;
    private HopperXpMode lastHopperXpMode;
    private HopperNBTMode lastHopperNBTMode;
    private HopperFluidMode lastHopperFluidMode;
    private HopperRangeMode lastHopperRangeMode;
    /** 上次发送的物品吸取档位索引 */
    private int lastItemTier = Integer.MIN_VALUE;
    /** 上次发送的流体吸取档位索引 */
    private int lastFluidTier = Integer.MIN_VALUE;

    public MagnetMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, InventoryHelper.findItemInPlayerInventory(playerInventory.player, BDItems.NET_MAGNET_ITEM.get()));
    }

    public MagnetMenu(int containerId, Inventory playerInventory, ItemStack menuStack) {
        super(ModMenus.MAGNET.get(), containerId, playerInventory);
        this.menuStack = menuStack;

        initialized = false;
        // 为服务端注入真实数据，客户端由槽位同步
        if (!playerInventory.player.level().isClientSide()) {
            List<KeyAmount> stacks = BaseMachineItem.getFilterSlotsOrDefault(menuStack, new ArrayList<>());
            for (int i = 0; i < stacks.size(); i++) {
                storage.insert(i, stacks.get(i).key(), stacks.get(i).amount(), false);
            }
        }
        initialized = true;

        addPlayerInv(playerInventory);
        addFlagSlots();
    }

    private void addFlagSlots() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                FlagStackTypedSlot flagSlot = new FlagStackTypedSlot(this, storage, row * 9 + col, 8 + col * 18, slotStartY + row * 18);
                this.addSlot(flagSlot);
            }
        }
    }

    private void addPlayerInv(Inventory playerInventory) {
        inventoryStartIndex = slots.size();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invSlotStartY + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 4 + invSlotStartY + 3 * 18));
        }
        inventoryEndIndex = slots.size();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return menuStack != null && !menuStack.isEmpty();
    }

    @Override
    protected boolean shouldSendQuickData() {
        boolean result = super.shouldSendQuickData()
                || lastControlMode != BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE)
                || lastFilterMode != BaseMachineItem.getFilterModeOrDefault(menuStack, FilterMode.BLACK)
                || lastHopperItemMode != BaseMachineItem.getHopperItemModeOrDefault(menuStack, HopperItemMode.ALLOW)
                || lastHopperXpMode != BaseMachineItem.getHopperXpModeOrDefault(menuStack, HopperXpMode.DENY)
                || lastHopperNBTMode != BaseMachineItem.getHopperNBTModeOrDefault(menuStack, HopperNBTMode.DENY)
                || lastHopperFluidMode != BaseMachineItem.getHopperFluidModeOrDefault(menuStack, HopperFluidMode.DENY)
                || lastHopperRangeMode != BaseMachineItem.getHopperRangeModeOrDefault(menuStack, HopperRangeMode.RADIUS_MID)
                || lastItemTier != MagnetSettings.getItemTierIndex(menuStack)
                || lastFluidTier != MagnetSettings.getFluidTierIndex(menuStack);

        if (result) {
            lastControlMode = BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE);
            lastFilterMode = BaseMachineItem.getFilterModeOrDefault(menuStack, FilterMode.BLACK);
            lastHopperItemMode = BaseMachineItem.getHopperItemModeOrDefault(menuStack, HopperItemMode.ALLOW);
            lastHopperXpMode = BaseMachineItem.getHopperXpModeOrDefault(menuStack, HopperXpMode.DENY);
            lastHopperNBTMode = BaseMachineItem.getHopperNBTModeOrDefault(menuStack, HopperNBTMode.DENY);
            lastHopperFluidMode = BaseMachineItem.getHopperFluidModeOrDefault(menuStack, HopperFluidMode.DENY);
            lastHopperRangeMode = BaseMachineItem.getHopperRangeModeOrDefault(menuStack, HopperRangeMode.RADIUS_MID);
            lastItemTier = MagnetSettings.getItemTierIndex(menuStack);
            lastFluidTier = MagnetSettings.getFluidTierIndex(menuStack);
        }

        return result;
    }

    @Override
    protected void writeQuickDataTag(CompoundTag tag) {
        super.writeQuickDataTag(tag);
        tag.putString("control_mode", BaseMachineItem.getControlModeOrDefault(menuStack, RedStoneControlMode.IGNORE).name());
        tag.putString("filter_type", BaseMachineItem.getFilterModeOrDefault(menuStack, FilterMode.BLACK).name());
        tag.putString("hopper_item_mode", BaseMachineItem.getHopperItemModeOrDefault(menuStack, HopperItemMode.ALLOW).name());
        tag.putString("hopper_xp_mode", BaseMachineItem.getHopperXpModeOrDefault(menuStack, HopperXpMode.DENY).name());
        tag.putString("hopper_nbt_mode", BaseMachineItem.getHopperNBTModeOrDefault(menuStack, HopperNBTMode.DENY).name());
        tag.putString("hopper_fluid_mode", BaseMachineItem.getHopperFluidModeOrDefault(menuStack, HopperFluidMode.DENY).name());
        tag.putString("hopper_range_mode", BaseMachineItem.getHopperRangeModeOrDefault(menuStack, HopperRangeMode.RADIUS_MID).name());
        tag.putInt(MagnetSettings.KEY_TIER_ITEM, MagnetSettings.getItemTierIndex(menuStack));
        tag.putInt(MagnetSettings.KEY_TIER_FLUID, MagnetSettings.getFluidTierIndex(menuStack));
    }

    @Override
    public void readQuickDataTag(CompoundTag tag) {
        super.readQuickDataTag(tag);
        BaseMachineItem.setControlMode(menuStack, RedStoneControlMode.valueOf(tag.getString("control_mode")));
        BaseMachineItem.setFilterMode(menuStack, FilterMode.valueOf(tag.getString("filter_type")));
        BaseMachineItem.setHopperItemMode(menuStack, HopperItemMode.valueOf(tag.getString("hopper_item_mode")));
        BaseMachineItem.setHopperXpMode(menuStack, HopperXpMode.valueOf(tag.getString("hopper_xp_mode")));
        BaseMachineItem.setHopperNBTMode(menuStack, HopperNBTMode.valueOf(tag.getString("hopper_nbt_mode")));
        BaseMachineItem.setHopperFluidMode(menuStack, HopperFluidMode.valueOf(tag.getString("hopper_fluid_mode")));
        BaseMachineItem.setHopperRangeMode(menuStack, HopperRangeMode.valueOf(tag.getString("hopper_range_mode")));
        if (tag.contains(MagnetSettings.KEY_TIER_ITEM)) {
            MagnetSettings.setItemTierIndex(menuStack, tag.getInt(MagnetSettings.KEY_TIER_ITEM));
        }
        if (tag.contains(MagnetSettings.KEY_TIER_FLUID)) {
            MagnetSettings.setFluidTierIndex(menuStack, tag.getInt(MagnetSettings.KEY_TIER_FLUID));
        }
    }
}
