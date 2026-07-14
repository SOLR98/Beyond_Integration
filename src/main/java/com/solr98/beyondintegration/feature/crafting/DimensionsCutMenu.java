package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DimensionsCutMenu extends DimensionsStorageMenu {
    public final StonecutterMenu delegate;
    private int wsS = -1;
    private static final int[] WX = {20, 143};
    private static final int WY = 20;
    private static final int WS_INPUT = 1, WS_OUTPUT = 1;
    private final DataSlot selectedRecipeIndex;
    private ItemStack lastIngredient = ItemStack.EMPTY;
    private final Player owningPlayer;

    public DimensionsCutMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.CUT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(
                        AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }
    public DimensionsCutMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        owningPlayer = inv.player;
        delegate = new StonecutterMenu(0, inv, ContainerLevelAccess.create(inv.player.level(), inv.player.blockPosition()));
        wsS = slots.size();
        for (int i = 0; i < 2; i++) {
            addSlot(delegate.getSlot(i));
            customSlotIndices.add(slots.size() - 1);
            setSlotX(slots.get(wsS + i), WX[i]);
            setSlotY(slots.get(wsS + i), ey(WY));
        }
        selectedRecipeIndex = DataSlot.standalone();
        selectedRecipeIndex.set(-1);
        addDataSlot(selectedRecipeIndex);

        if (delegate.getSlot(0).container instanceof net.minecraft.world.SimpleContainer sc) {
            sc.addListener(new ContainerListener() {
                @Override public void containerChanged(Container c) {
                    if (owningPlayer != null && !owningPlayer.level().isClientSide())
                        autoRefill(owningPlayer);
                }
            });
        }
    }
    @Override public void rebuildSlots() { super.rebuildSlots(); if(wsS>=0){int y=ey(WY);for(int i=0;i<2;i++){setSlotX(slots.get(wsS+i),WX[i]);setSlotY(slots.get(wsS+i),y);}} }
    public ItemStack getOutput() { return delegate.getSlot(WS_OUTPUT).getItem(); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (delegate.clickMenuButton(player, id)) {
            selectedRecipeIndex.set(delegate.getSelectedRecipeIndex());
            ItemStack input = delegate.getSlot(0).getItem();
            if (!input.isEmpty()) lastIngredient = input.copyWithCount(1);
            return true;
        }
        return false;
    }

    private void autoRefill(Player player) {
        if (player.level().isClientSide()) return;
        if (!delegate.getSlot(0).getItem().isEmpty()) return;
        if (lastIngredient.isEmpty()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer((ServerPlayer) player);
        if (net == null) return;
        var storage = net.getUnifiedStorage();
        ItemStackKey key = new ItemStackKey(lastIngredient);
        KeyAmount extracted = storage.extract(key, 1, false, false);
        if (extracted.amount() > 0) {
            delegate.getSlot(0).set(lastIngredient.copyWithCount((int) extracted.amount()));
            delegate.getSlot(0).setChanged();
        }
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
                autoRefill(player);
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

    @Override public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(p);
        int[] allSlots = {0, WS_OUTPUT};
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

    public List<StonecutterRecipe> getRecipes() {
        return delegate.getRecipes();
    }

    public int getNumRecipes() {
        return delegate.getNumRecipes();
    }

    public int getSelectedRecipeIndex() {
        return selectedRecipeIndex.get();
    }
}
