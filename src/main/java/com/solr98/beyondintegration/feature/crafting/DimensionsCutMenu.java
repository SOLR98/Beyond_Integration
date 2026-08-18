package com.solr98.beyondintegration.feature.crafting;

import com.google.common.collect.Lists;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// BeyondIntegration copy 自原版 StonecutterMenu(1.20.1) 改造：
// 私有字段/方法统一 beyond$ 前缀标识。
// 改造点：继承网络存储菜单、autoRefill（输入耗尽从网络补料）、快捷移动适配网络存储、
// 配方选择恢复（补料后按上次配方 ID 重新选中）。
/**
 * 切石机菜单（继承网络存储菜单）：输入槽/结果槽 + 网络存储。
 * 结果槽取走时消耗输入槽存量，耗尽后自动从网络补料（最多 64）实现持续合成；
 * 补料后按上次配方 ID 恢复选中配方并重新装配结果。
 */
public class DimensionsCutMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final int WS_INPUT = 1, WS_OUTPUT = 1;
    private static final int[] WX = {20, 143};
    private static final int WY = 20;

    // ---- copy 原版 StonecutterMenu 私有状态（beyond$ 前缀）----
    // 输入槽容器（1 格）：变化触发配方列表重建与更新监听
    private final SimpleContainer beyond$container = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            DimensionsCutMenu.this.slotsChanged(this);
            DimensionsCutMenu.this.beyond$slotUpdateListener.run();
        }
    };
    private final ResultContainer beyond$resultContainer = new ResultContainer();
    private List<StonecutterRecipe> beyond$recipes = Lists.newArrayList();
    // 当前选中配方索引（DataSlot 双向同步）
    private final DataSlot beyond$selectedRecipeIndex = DataSlot.standalone();
    private ItemStack beyond$input = ItemStack.EMPTY;
    private long beyond$lastSoundTime;
    private Runnable beyond$slotUpdateListener = () -> {};
    private int beyond$wsS = -1;
    // 上次选中的配方 ID：输入变空触发 setupRecipeList 会重置选择（-1），补料后按 ID 恢复并重新装配结果
    private net.minecraft.resources.ResourceLocation beyond$lastSelectedRecipeId = null;
    // 上次消耗的输入材料（耗尽后补料用）；本轮 onTake 是否补料（用于终止 shift 循环）
    private ItemStack beyond$lastIngredient = ItemStack.EMPTY;
    private boolean beyond$justRefilled = false;
    // 菜单所属玩家
    private final Player owningPlayer;

    public DimensionsCutMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.CUT.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(
                        AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    public DimensionsCutMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        owningPlayer = inv.player;
        beyond$wsS = slots.size();
        // 输入槽
        addSlot(new Slot(beyond$container, 0, WX[0], ey(WY)));
        customSlotIndices.add(slots.size() - 1);
        // 结果槽（BD 合成体验：onTake 消耗输入槽材料，耗尽后从网络补料，可持续合成）
        // ResultSlot 子类：BD quickMoveHandle 的批量合成分支（64 上限 + 每轮 onTake 补料 + 回滚）依赖 instanceof ResultSlot
        addSlot(new ResultSlot(player, new TransientCraftingContainer(this, 1, 1), beyond$resultContainer, 1, WX[1], ey(WY)) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player p, ItemStack st) {
                if (p.level().isClientSide()) return;
                beyond$justRefilled = false;
                st.onCraftedBy(p.level(), p, st.getCount());
                beyond$resultContainer.awardUsedRecipes(p, List.of(beyond$container.getItem(0)));
                ItemStack input = beyond$container.getItem(0);
                if (!input.isEmpty()) {
                    // 合成只消耗输入槽存量，绝不在合成过程中直接消耗网络材料
                    beyond$lastIngredient = input.copyWithCount(1);
                    beyond$container.removeItem(0, 1);
                }
                // 输入耗尽 → 循环结束后补料一次（最多 64）→ 由 quickMoveStack 终止循环
                if (beyond$container.getItem(0).isEmpty() && !beyond$lastIngredient.isEmpty()) {
                    refillTo64(p);
                    beyond$justRefilled = true;
                }
                recalcResult(p);
                long l = p.level().getGameTime();
                if (beyond$lastSoundTime != l) {
                    p.level().playSound(null, p.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    beyond$lastSoundTime = l;
                }
                super.onTake(p, st);
            }
        });
        customSlotIndices.add(slots.size() - 1);
        this.addDataSlot(this.beyond$selectedRecipeIndex);
    }

    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { int y = ey(WY); for (int i = 0; i < 2; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), y); } }
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getOutput() { return beyond$resultContainer.getItem(0); }
    public ItemStack getInput() { return beyond$container.getItem(0); }
    public List<StonecutterRecipe> getRecipes() { return beyond$recipes; }
    public int getNumRecipes() { return beyond$recipes.size(); }
    public int getSelectedRecipeIndex() { return beyond$selectedRecipeIndex.get(); }
    public boolean hasInputItem() { return !beyond$container.getItem(0).isEmpty() && !beyond$recipes.isEmpty(); }

    // 注册槽位变化监听（GUI 刷新配方/选中用）
    public void registerUpdateListener(Runnable listener) { this.beyond$slotUpdateListener = listener; }

    // ---- copy 原版 StonecutterMenu.slotsChanged / setupRecipeList / setupResultSlot ----
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        ItemStack itemstack = beyond$container.getItem(0);
        if (!itemstack.is(this.beyond$input.getItem())) {
            this.beyond$input = itemstack.copy();
            this.beyond$setupRecipeList(inventory, itemstack);
        }
    }

    private void beyond$setupRecipeList(Container inventory, ItemStack input) {
        this.beyond$recipes.clear();
        this.beyond$selectedRecipeIndex.set(-1);
        this.slots.get(beyond$wsS + WS_OUTPUT).set(ItemStack.EMPTY);
        if (!input.isEmpty()) {
            this.beyond$recipes = this.owningPlayer.level().getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, inventory, this.owningPlayer.level());
        }
    }

    private void beyond$setupResultSlot() {
        if (!this.beyond$recipes.isEmpty() && this.beyond$isValidRecipeIndex(this.beyond$selectedRecipeIndex.get())) {
            StonecutterRecipe stonecutterrecipe = this.beyond$recipes.get(this.beyond$selectedRecipeIndex.get());
            ItemStack itemstack = stonecutterrecipe.assemble(this.beyond$container, this.owningPlayer.level().registryAccess());
            if (itemstack.isItemEnabled(this.owningPlayer.level().enabledFeatures())) {
                this.beyond$resultContainer.setRecipeUsed(stonecutterrecipe);
                this.slots.get(beyond$wsS + WS_OUTPUT).set(itemstack);
            } else {
                this.slots.get(beyond$wsS + WS_OUTPUT).set(ItemStack.EMPTY);
            }
        } else {
            this.slots.get(beyond$wsS + WS_OUTPUT).set(ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    private boolean beyond$isValidRecipeIndex(int index) {
        return index >= 0 && index < this.beyond$recipes.size();
    }

    // 配方选中：设置选中索引、装配结果并记录上次配方 ID（供补料后恢复）
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.beyond$isValidRecipeIndex(id)) {
            this.beyond$selectedRecipeIndex.set(id);
            this.beyond$setupResultSlot();
            ItemStack input = beyond$container.getItem(0);
            if (!input.isEmpty()) beyond$lastIngredient = input.copyWithCount(1);
            if (id < beyond$recipes.size())
                beyond$lastSelectedRecipeId = beyond$recipes.get(id).getId();
        }
        return true;
    }

    // 重算结果槽：输入变空触发 setupRecipeList 会重置选中（-1），补料（同 ID）后跳过重算；
    // 优先按上次配方 ID 恢复选择，否则当前选择有效则直接重算
    private void recalcResult(Player p) {
        if (beyond$lastSelectedRecipeId != null) {
            List<StonecutterRecipe> recipes = beyond$recipes;
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i).getId().equals(beyond$lastSelectedRecipeId)) {
                    this.beyond$selectedRecipeIndex.set(i);
                    this.beyond$setupResultSlot();
                    return;
                }
            }
        }
        int sel = beyond$selectedRecipeIndex.get();
        if (sel >= 0 && sel < beyond$recipes.size())
            this.beyond$setupResultSlot();
    }

    // 从网络补料至输入槽（最多 64）；仅在合成循环耗尽后调用一次，不参与合成消耗
    private void refillTo64(Player player) {
        if (player.level().isClientSide()) return;
        if (!beyond$container.getItem(0).isEmpty()) return;
        if (beyond$lastIngredient.isEmpty()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer((ServerPlayer) player);
        if (net == null) return;
        int want = beyond$container.getMaxStackSize();
        KeyAmount extracted = net.getUnifiedStorage().extract(new ItemStackKey(beyond$lastIngredient), want, false, false);
        if (extracted.amount() > 0) {
            beyond$container.setItem(0, beyond$lastIngredient.copyWithCount((int) extracted.amount()));
            beyond$container.setChanged();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        Item item = stack.getItem();
        if (slotIndex == beyond$wsS + WS_OUTPUT) {
            // shift 合成：原版 while 循环逐轮驱动；合成只消耗输入槽存量，
            // 耗尽后 onTake 补料一次并置标志 → 此处返回 EMPTY 终止循环
            item.onCraftedBy(stack, player.level(), player);
            int beforeCount = stack.getCount();
            moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) { long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount(); stack.setCount((int) remaining); }
            }
            if (stack.getCount() < beforeCount) {
                slot.onQuickCraft(result, stack);
                slot.onTake(player, result);
                if (beyond$justRefilled) { beyond$justRefilled = false; return ItemStack.EMPTY; }
                return result;
            }
            return ItemStack.EMPTY;
        }
        if (slotIndex == beyond$wsS + INPUT_SLOT) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, false)) return ItemStack.EMPTY;
            slot.setChanged(); return result;
        }
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            // 背包 → 输入槽（原版：先检查是否切石配方材料）
            if (this.owningPlayer.level().getRecipeManager().getRecipeFor(RecipeType.STONECUTTING, new SimpleContainer(stack), this.owningPlayer.level()).isPresent()) {
                Slot target = this.slots.get(beyond$wsS + INPUT_SLOT);
                if (target.mayPlace(stack)) {
                    if (target.getItem().isEmpty()) { int n = Math.min(stack.getCount(), target.getMaxStackSize(stack)); target.set(stack.split(n)); target.setChanged(); }
                    else if (ItemStack.isSameItemSameTags(stack, target.getItem())) { int space = target.getMaxStackSize(stack) - target.getItem().getCount(); if (space > 0) { int n = Math.min(stack.getCount(), space); target.getItem().grow(n); stack.shrink(n); target.setChanged(); } }
                    if (stack.isEmpty()) { slot.setChanged(); return result; }
                }
            }
        }
        return super.quickMoveStack(player, slotIndex);
    }

    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlots(firstCraftReturnDir);
    }

    @Override
    public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$container, new int[]{0});
        // 结果槽对齐原版 StonecutterMenu.removed：关闭时直接丢弃，不归还
        beyond$resultContainer.setItem(0, ItemStack.EMPTY);
    }
}
