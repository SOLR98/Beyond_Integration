package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class DimensionsGrindMenu extends DimensionsStorageMenu {
    public final GrindstoneMenu delegate;
    private int wsS = -1;
    private static final int[] WX = {49, 49, 129};
    private static final int[] WY = {6, 27, 21};
    private static final int WS_INPUT = 2, WS_OUTPUT = 2;

    public DimensionsGrindMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.GRIND.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(
                        AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }
    public DimensionsGrindMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        delegate = new GrindstoneMenu(0, inv, ContainerLevelAccess.create(inv.player.level(), inv.player.blockPosition()));
        wsS = slots.size();
        for (int i = 0; i < 3; i++) {
            addSlot(delegate.getSlot(i));
            customSlotIndices.add(slots.size() - 1);
            setSlotX(slots.get(wsS + i), WX[i]);
            setSlotY(slots.get(wsS + i), ey(WY[i]));
        }
    }

    @Override public void rebuildSlots() { super.rebuildSlots(); if(wsS>=0){for(int i=0;i<3;i++){setSlotX(slots.get(wsS+i),WX[i]);setSlotY(slots.get(wsS+i),ey(WY[i]));}} }
    public ItemStack getOutput() { return delegate.getSlot(WS_OUTPUT).getItem(); }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        delegate.slotsChanged(container);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (slotIndex == wsS + WS_OUTPUT) {
            int beforeCount = stack.getCount();
            moveItemStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) {
                    long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount();
                    stack.setCount((int) remaining);
                }
            }
            if (stack.getCount() < beforeCount) {
                for (int i = 0; i < WS_INPUT; i++)
                    delegate.getSlot(i).set(ItemStack.EMPTY);
                slot.onQuickCraft(result, stack);
                slot.onTake(player, result);
                return result;
            }
            return ItemStack.EMPTY;
        }

        if (slotIndex >= wsS && slotIndex < wsS + WS_INPUT) {
            if (!moveItemStackTo(stack, inventoryStartIndex, inventoryEndIndex, true))
                return ItemStack.EMPTY;
            slot.setChanged();
            return result;
        }

        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            for (int i = 0; i < WS_INPUT && !stack.isEmpty(); i++) {
                Slot target = this.slots.get(wsS + i);
                if (!target.mayPlace(stack)) continue;
                ItemStack targetStack = target.getItem();
                if (targetStack.isEmpty()) {
                    int n = Math.min(stack.getCount(), target.getMaxStackSize(stack));
                    target.set(stack.split(n));
                    target.setChanged();
                } else if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                    int space = target.getMaxStackSize(stack) - targetStack.getCount();
                    if (space > 0) {
                        int n = Math.min(stack.getCount(), space);
                        targetStack.grow(n);
                        stack.shrink(n);
                        target.set(targetStack);
                        target.setChanged();
                    }
                }
            }
            if (stack.isEmpty()) {
                slot.setChanged();
                return result;
            }
        }

        return super.quickMoveStack(player, slotIndex);
    }

    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(p);
        int[] allSlots = {0, 1, WS_OUTPUT};
        for (int idx : allSlots) {
            ItemStack s = delegate.getSlot(idx).getItem();
            if (s.isEmpty()) continue;
            delegate.getSlot(idx).set(ItemStack.EMPTY);
            p.getInventory().add(s);
            if (!s.isEmpty() && net != null) {
                long remaining = net.getUnifiedStorage().insert(new ItemStackKey(s), s.getCount(), false).amount();
                s.setCount((int) remaining);
            }
            if (!s.isEmpty()) p.drop(s, false);
        }
    }
}
