package com.solr98.beyondintegration.feature.crafting;

import com.mojang.datafixers.util.Pair;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;

public class DimensionsCraftMenu extends DimensionsStorageMenu {
    private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final Player player;
    @Override public int getPanelHeight() { return 72; }

    private int gyOff() { return 68 + (getLines() - 2) * 18; }

    public DimensionsCraftMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.CRAFT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(
                        AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    public DimensionsCraftMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        this.player = inv.player;
        int gapOff = gyOff();

        // 3×3 合成格 (相对面板: x=77, y=1)
        for (int r = 0; r < 3; ++r)
            for (int c = 0; c < 3; ++c) {
                int idx = c + r * 3;
                addSlot(new Slot(craftSlots, idx, 77 + c * 18, gapOff + 1 + r * 18) {
                    @Override public void setChanged() {
                        super.setChanged();
                        slotsChanged(craftSlots);
                    }
                });
                customSlotIndices.add(slots.size() - 1);
            }

        // 结果槽 (相对面板: x=153, y=20) — 带自动补料
        addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, 153, gapOff + 20) {
            @Override
            public void onTake(Player p, ItemStack stack) {
                stack.onCraftedBy(p.level(), p, 1);
                // 自动补料：优先网络 → 玩家背包 → 合成格
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(p);
                var storage = net != null ? net.getUnifiedStorage() : null;
                NonNullList<ItemStack> remainders = p.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, craftSlots, p.level());
                for (int idx = 0; idx < 9; idx++) {
                    ItemStack slotStack = craftSlots.getItem(idx);
                    ItemStack remainder = remainders.get(idx);
                    if (!slotStack.isEmpty()) {
                        int need = 1;
                        if (slotStack.getCount() <= need) {
                            ItemStackKey key = new ItemStackKey(slotStack);
                            if (storage != null) {
                                need -= (int) storage.extract(key, need, false, false).amount();
                            }
                            for (int i = 0; i < p.getInventory().items.size() && need > 0; i++) {
                                ItemStack inv = p.getInventory().items.get(i);
                                if (ItemStack.isSameItemSameTags(inv, key.getReadOnlyStack())) {
                                    int take = Math.min(need, inv.getCount());
                                    inv.shrink(take);
                                    p.getInventory().items.set(i, inv.isEmpty() ? ItemStack.EMPTY : inv);
                                    need -= take;
                                }
                            }
                        }
                        if (need > 0) craftSlots.removeItem(idx, need);
                    }
                    // 处理返回物（如空桶/熔炉等）
                    if (!remainder.isEmpty()) {
                        int rc = 1;
                        ItemStackKey rk = new ItemStackKey(remainder);
                        ItemStack current = craftSlots.getItem(idx);
                        if (current.isEmpty()) { craftSlots.setItem(idx, rk.copyStackWithCount(1)); rc--; }
                        if (rc > 0 && storage != null) rc = (int) storage.insert(rk, rc, false).amount();
                        if (rc > 0) { ItemStack ins = rk.copyStackWithCount(rc); p.getInventory().add(ins); rc = ins.getCount(); }
                        if (rc > 0) p.drop(rk.copyStackWithCount(rc), false);
                    }
                }
                if (CommandConfig.enableAuditLog() && net != null) {
                    BindingAuditLog.log(new AuditEntry(
                            System.currentTimeMillis(), "GUI_CRAFT",
                            p.getName().getString(), p.getUUID(),
                            net.getId(), "ITEM", stack.getItem().toString(),
                            true, "crafted " + stack.getCount() + "x " + stack.getDisplayName().getString()));
                }
                this.container.setItem(0, ItemStack.EMPTY);
                slotsChanged(craftSlots);
            }
        });
        customSlotIndices.add(slots.size() - 1);

        // 盔甲槽 (相对面板: x=8, y=1/19/37/55)
        Inventory pi = inv;
        addSlot(new Slot(pi, 39, 8, gapOff + 1) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.HEAD, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.HEAD, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addSlot(new Slot(pi, 38, 8, gapOff + 19) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.CHEST, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.CHEST, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addSlot(new Slot(pi, 37, 8, gapOff + 37) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.LEGS, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.LEGS, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        addSlot(new Slot(pi, 36, 8, gapOff + 55) {
            @Override public boolean mayPlace(ItemStack s) { return s.canEquip(EquipmentSlot.FEET, player); }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.FEET, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        // 副手槽 (相对面板: x=77, y=55)
        addSlot(new Slot(pi, 40, 77, gapOff + 55) {
            @Override public boolean mayPlace(ItemStack s) { return true; }
            @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
            @Override public void setByPlayer(ItemStack newStack) {
                ItemStack oldStack = getItem().copy();
                super.setByPlayer(newStack);
                player.onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
            }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    @Override public void rebuildSlots() {
        super.rebuildSlots();
        int gapOff = gyOff();
        var ci = customSlotIndices;
        if (ci.size() < 15) return;
        // 合成格 (0-8)
        for (int r = 0; r < 3; ++r)
            for (int c = 0; c < 3; ++c) {
                setSlotX(slots.get(ci.get(r * 3 + c)), 77 + c * 18);
                setSlotY(slots.get(ci.get(r * 3 + c)), gapOff + 1 + r * 18);
            }
        // 结果槽 (9)
        setSlotX(slots.get(ci.get(9)), 153);
        setSlotY(slots.get(ci.get(9)), gapOff + 20);
        // 盔甲槽 (10-13)
        for (int i = 0; i < 4; i++) {
            setSlotX(slots.get(ci.get(10 + i)), 8);
            setSlotY(slots.get(ci.get(10 + i)), gapOff + 1 + i * 18);
        }
        // 副手槽 (14)
        setSlotX(slots.get(ci.get(14)), 77);
        setSlotY(slots.get(ci.get(14)), gapOff + 55);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container != craftSlots) return;
        var level = player.level();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
        if (recipe.isPresent()) {
            CraftingRecipe r = recipe.get();
            resultSlots.setItem(0, r.assemble(craftSlots, level.registryAccess()));
        } else {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (slotIndex == 9) {
            NonNullList<ItemStack> remainders = player.level().getRecipeManager().getRemainingItemsFor(
                    RecipeType.CRAFTING, craftSlots, player.level());
            for (int i = 0; i < 9; i++) {
                ItemStack s = craftSlots.getItem(i);
                if (s.isEmpty()) continue;
                s.shrink(1);
                if (s.isEmpty()) craftSlots.setItem(i, remainders.get(i));
            }
            slotsChanged(craftSlots);
            if (CommandConfig.enableAuditLog()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) {
                    BindingAuditLog.log(new AuditEntry(
                            System.currentTimeMillis(), "GUI_CRAFT",
                            player.getName().getString(), player.getUUID(),
                            net.getId(), "ITEM", stack.getItem().toString(),
                            true, "shift-crafted " + result.getCount() + "x " + result.getDisplayName().getString()));
                }
            }
            int before = stack.getCount();
            moveItemStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) {
                    long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount();
                    stack.setCount((int) remaining);
                }
            }
            if (stack.getCount() < before) return result;
            return ItemStack.EMPTY;
        }

        // 合成格 (0-8) → 背包
        if (slotIndex >= 0 && slotIndex < 9) {
            if (!moveItemStackTo(stack, inventoryStartIndex, inventoryEndIndex, true))
                return ItemStack.EMPTY;
            slot.setChanged();
            slotsChanged(craftSlots);
            return result;
        }

        // 盔甲槽 (10-13) → 背包
        if (slotIndex >= 10 && slotIndex < 14) {
            if (!moveItemStackTo(stack, inventoryStartIndex, inventoryEndIndex, true))
                return ItemStack.EMPTY;
            slot.setChanged();
            return result;
        }

        // 背包 → 合成格/盔甲槽/副手
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            for (int i = 0; i < 15 && !stack.isEmpty(); i++) {
                if (i == 9) continue; // 跳过结果槽
                Slot t = this.slots.get(i);
                if (!t.mayPlace(stack)) continue;
                ItemStack ts = t.getItem();
                if (ts.isEmpty()) {
                    int n = Math.min(stack.getCount(), t.getMaxStackSize(stack));
                    t.set(stack.split(n));
                    t.setChanged();
                } else if (ItemStack.isSameItemSameTags(ts, stack)) {
                    int space = t.getMaxStackSize(stack) - ts.getCount();
                    if (space > 0) {
                        int n = Math.min(stack.getCount(), space);
                        ts.grow(n);
                        stack.shrink(n);
                        t.set(ts);
                        t.setChanged();
                    }
                }
            }
            if (stack.isEmpty()) {
                slot.setChanged();
                slotsChanged(craftSlots);
                return result;
            }
        }
        return super.quickMoveStack(player, slotIndex);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) { return false; }

    // ── 合成格转移按钮 ──
    public void cleanCraftSlots(boolean toStorage) {
        if (player.level().isClientSide()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer((ServerPlayer) player);
        var storage = net != null ? net.getUnifiedStorage() : null;
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.getItem(i);
            if (s.isEmpty()) continue;
            craftSlots.setItem(i, ItemStack.EMPTY);
            if (toStorage) {
                if (storage != null) {
                    long left = storage.insert(new ItemStackKey(s), s.getCount(), false).amount();
                    s.setCount((int) left);
                }
                if (!s.isEmpty()) player.getInventory().add(s);
                if (!s.isEmpty()) player.drop(s, false);
            } else {
                player.getInventory().add(s);
                if (!s.isEmpty() && storage != null) {
                    long left = storage.insert(new ItemStackKey(s), s.getCount(), false).amount();
                    s.setCount((int) left);
                }
                if (!s.isEmpty()) player.drop(s, false);
            }
        }
        slotsChanged(craftSlots);
    }

    // ── JEI/EMI 配方填充 ──
    public void transferRecipe(java.util.List<IStackKey<?>> inputKeys, java.util.List<Long> amounts) {
        // 清空合成格（物品送回背包）
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.getItem(i);
            if (s.isEmpty()) continue;
            craftSlots.setItem(i, ItemStack.EMPTY);
            player.getInventory().add(s);
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer((ServerPlayer) player);
        var storage = net != null ? net.getUnifiedStorage() : null;
        int limit = Math.min(9, inputKeys.size());
        for (int i = 0; i < limit; i++) {
            if (!(inputKeys.get(i) instanceof ItemStackKey isk)) continue;
            long needL = i < amounts.size() ? amounts.get(i) : 0;
            if (needL <= 0) continue;
            int need = (int) Math.min(Integer.MAX_VALUE, needL);
            // 背包优先
            for (int j = 0; j < 36 && need > 0; j++) {
                ItemStack inv = player.getInventory().getItem(j);
                if (ItemStack.isSameItemSameTags(inv, isk.getReadOnlyStack())) {
                    int take = Math.min(need, inv.getCount());
                    inv.shrink(take);
                    player.getInventory().setItem(j, inv.isEmpty() ? ItemStack.EMPTY : inv);
                    need -= take;
                }
            }
            // 网络其次
            if (need > 0 && storage != null) {
                need -= (int) storage.extract(isk, need, false, false).amount();
            }
            int got = (int) Math.min(needL, needL - need);
            if (got > 0) craftSlots.setItem(i, isk.copyStackWithCount(got));
        }
        slotsChanged(craftSlots);
    }

    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(p);
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.getItem(i);
            if (s.isEmpty()) continue;
            craftSlots.setItem(i, ItemStack.EMPTY);
            p.getInventory().add(s);
            if (!s.isEmpty() && net != null) {
                long remaining = net.getUnifiedStorage().insert(new ItemStackKey(s), s.getCount(), false).amount();
                s.setCount((int) remaining);
            }
            if (!s.isEmpty()) p.drop(s, false);
        }
    }
}
