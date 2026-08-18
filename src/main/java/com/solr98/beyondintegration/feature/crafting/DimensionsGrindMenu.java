package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

// BeyondIntegration copy 自原版 GrindstoneMenu(1.20.1) 改造：
// 私有字段/方法统一 beyond$ 前缀标识。
// 改造点：继承网络存储菜单、经验球奖励/音效改在玩家位置（去 ContainerLevelAccess）、
// 快捷移动适配网络存储（经验球不因预清空输入槽而丢失）。
/**
 * 砂轮菜单（继承网络存储菜单）：双输入槽 + 结果槽。
 * copy 原版 GrindstoneMenu 改造：经验球奖励/音效改在玩家位置（去除 ContainerLevelAccess），
 * 快捷移动适配网络存储且不预清空输入槽（保证经验球奖励）。
 */
public class DimensionsGrindMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int WS_INPUT = 2, WS_OUTPUT = 2;
    private static final int[] WX = {49, 49, 129};
    private static final int[] WY = {6, 27, 21};

    // ---- copy 原版 GrindstoneMenu 私有状态（beyond$ 前缀）----
    // 双输入槽容器（可修复物/附魔书/已附魔物等）：变化触发重算结果
    final Container beyond$repairSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            DimensionsGrindMenu.this.slotsChanged(this);
        }
    };
    final Container beyond$resultSlots = new ResultContainer();
    // 缓存的经验值（ForgeHooks.onGrindstoneChange 计算，-1 表示未缓存）
    private int beyond$xp = -1;
    private int beyond$wsS = -1;

    public DimensionsGrindMenu(int id, Inventory inv, net.minecraft.network.FriendlyByteBuf b) {
        this(ModMenus.GRIND.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(
                        AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    public DimensionsGrindMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        beyond$wsS = slots.size();
        // 输入槽 0/1（原版 mayPlace 规则）
        for (int i = 0; i < 2; i++) {
            addSlot(new Slot(beyond$repairSlots, i, WX[i], ey(WY[i])) {
                @Override public boolean mayPlace(ItemStack s) {
                    return s.isDamageableItem() || s.is(Items.ENCHANTED_BOOK) || s.isEnchanted() || s.canGrindstoneRepair();
                }
            });
            customSlotIndices.add(slots.size() - 1);
        }
        // 结果槽 2（原版 onTake：GrindstoneTake hook + 经验球奖励 + 音效 + 清双槽）
        addSlot(new Slot(beyond$resultSlots, 0, WX[2], ey(WY[2])) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player p, ItemStack st) {
                if (p.level().isClientSide()) return;
                ContainerLevelAccess access = ContainerLevelAccess.create(p.level(), p.blockPosition());
                if (ForgeHooks.onGrindstoneTake(beyond$repairSlots, access, lvl -> beyond$getExperienceAmount(lvl))) return;
                if (p.level() instanceof ServerLevel serverLevel) {
                    ExperienceOrb.award(serverLevel, Vec3.atCenterOf(p.blockPosition()), beyond$getExperienceAmount(p.level()));
                }
                p.level().levelEvent(1042, p.blockPosition(), 0);
                beyond$repairSlots.setItem(0, ItemStack.EMPTY);
                beyond$repairSlots.setItem(1, ItemStack.EMPTY);
            }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) { for (int i = 0; i < 3; i++) { setSlotX(slots.get(beyond$wsS + i), WX[i]); setSlotY(slots.get(beyond$wsS + i), ey(WY[i])); } }
    }

    // ---- BI 公开 API（GUI 使用）----
    public ItemStack getOutput() { return beyond$resultSlots.getItem(0); }
    public ItemStack getInput() { return beyond$repairSlots.getItem(0); }
    public ItemStack getAdditional() { return beyond$repairSlots.getItem(1); }

    // ---- copy 原版 GrindstoneMenu.slotsChanged ----
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.beyond$repairSlots) {
            this.beyond$createResult();
        }
    }

    // ---- copy 原版 GrindstoneMenu.createResult（含 GrindstoneChange hook）----
    private void beyond$createResult() {
        ItemStack itemstack = this.beyond$repairSlots.getItem(0);
        ItemStack itemstack1 = this.beyond$repairSlots.getItem(1);
        boolean flag = !itemstack.isEmpty() || !itemstack1.isEmpty();
        boolean flag1 = !itemstack.isEmpty() && !itemstack1.isEmpty();
        this.beyond$xp = ForgeHooks.onGrindstoneChange(itemstack, itemstack1, this.beyond$resultSlots, -1);
        if (this.beyond$xp == Integer.MIN_VALUE) {
            if (!flag) {
                this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
            } else {
                boolean flag2 = !itemstack.isEmpty() && !itemstack.is(Items.ENCHANTED_BOOK) && !itemstack.isEnchanted() || !itemstack1.isEmpty() && !itemstack1.is(Items.ENCHANTED_BOOK) && !itemstack1.isEnchanted();
                if (itemstack.getCount() > 1 || itemstack1.getCount() > 1 || !flag1 && flag2) {
                    this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                    this.broadcastChanges();
                    return;
                }
                int j = 1;
                int i;
                ItemStack itemstack2;
                if (flag1) {
                    if (!itemstack.is(itemstack1.getItem())) {
                        this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                        this.broadcastChanges();
                        return;
                    }
                    Item item = itemstack.getItem();
                    int k = itemstack.getMaxDamage() - itemstack.getDamageValue();
                    int l = itemstack.getMaxDamage() - itemstack1.getDamageValue();
                    int i1 = k + l + itemstack.getMaxDamage() * 5 / 100;
                    i = Math.max(itemstack.getMaxDamage() - i1, 0);
                    itemstack2 = this.beyond$mergeEnchants(itemstack, itemstack1);
                    if (!itemstack2.isRepairable()) i = itemstack.getDamageValue();
                    if (!itemstack2.isDamageableItem() || !itemstack2.isRepairable()) {
                        if (!ItemStack.matches(itemstack, itemstack1)) {
                            this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                            this.broadcastChanges();
                            return;
                        }
                        j = 2;
                    }
                } else {
                    boolean flag3 = !itemstack.isEmpty();
                    i = flag3 ? itemstack.getDamageValue() : itemstack1.getDamageValue();
                    itemstack2 = flag3 ? itemstack : itemstack1;
                }
                // Forge: Skip the repair if the result would give an item stack with a count not normally obtainable
                if (j > itemstack2.getMaxStackSize())
                    this.beyond$resultSlots.setItem(0, ItemStack.EMPTY);
                else
                    this.beyond$resultSlots.setItem(0, this.beyond$removeNonCurses(itemstack2, i, j));
            }
        }
        this.broadcastChanges();
    }

    // ---- copy 原版 GrindstoneMenu.mergeEnchants ----
    private ItemStack beyond$mergeEnchants(ItemStack base, ItemStack addition) {
        ItemStack itemstack = base.copy();
        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(addition);
        for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!enchantment.isCurse() || EnchantmentHelper.getTagEnchantmentLevel(enchantment, itemstack) == 0) {
                itemstack.enchant(enchantment, entry.getValue());
            }
        }
        return itemstack;
    }

    // ---- copy 原版 GrindstoneMenu.removeNonCurses ----
    private ItemStack beyond$removeNonCurses(ItemStack input, int damage, int count) {
        ItemStack itemstack = input.copyWithCount(count);
        itemstack.removeTagKey("Enchantments");
        itemstack.removeTagKey("StoredEnchantments");
        if (damage > 0) {
            itemstack.setDamageValue(damage);
        } else {
            itemstack.removeTagKey("Damage");
        }
        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(input).entrySet().stream()
                .filter(e -> e.getKey().isCurse())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        EnchantmentHelper.setEnchantments(map, itemstack);
        itemstack.setRepairCost(0);
        if (itemstack.is(Items.ENCHANTED_BOOK) && map.size() == 0) {
            itemstack = new ItemStack(Items.BOOK);
            if (input.hasCustomHoverName()) {
                itemstack.setHoverName(input.getHoverName());
            }
        }
        for (int i = 0; i < map.size(); ++i) {
            itemstack.setRepairCost(DimensionsAnvilMenu.beyond$calculateIncreasedRepairCost(itemstack.getBaseRepairCost()));
        }
        return itemstack;
    }

    // ---- copy 原版 GrindstoneMenu 经验计算（含缓存 xp）----
    private int beyond$getExperienceAmount(Level level) {
        if (beyond$xp > -1) return beyond$xp;
        int l = 0;
        l += this.beyond$getExperienceFromItem(this.beyond$repairSlots.getItem(0));
        l += this.beyond$getExperienceFromItem(this.beyond$repairSlots.getItem(1));
        if (l > 0) {
            int i1 = (int) Math.ceil((double) l / 2.0D);
            return i1 + level.random.nextInt(i1);
        } else {
            return 0;
        }
    }

    // 单物品可磨经验：非诅咒附魔的最低费用之和
    private int beyond$getExperienceFromItem(ItemStack stack) {
        int l = 0;
        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
        for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer integer = entry.getValue();
            if (!enchantment.isCurse()) {
                l += enchantment.getMinCost(integer);
            }
        }
        return l;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (slotIndex == beyond$wsS + WS_OUTPUT) {
            int beforeCount = stack.getCount();
            moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true);
            if (!stack.isEmpty()) {
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
                if (net != null) { long remaining = net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false).amount(); stack.setCount((int) remaining); }
            }
            if (stack.getCount() < beforeCount) {
                // 材料清空/经验球由 onTake 完成，不可预清空输入槽
                slot.onQuickCraft(result, stack);
                slot.onTake(player, result);
                return result;
            }
            return ItemStack.EMPTY;
        }
        if (slotIndex >= beyond$wsS && slotIndex < beyond$wsS + WS_INPUT) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            slot.setChanged(); return result;
        }
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            for (int i = 0; i < WS_INPUT && !stack.isEmpty(); i++) {
                Slot target = this.slots.get(beyond$wsS + i);
                if (!target.mayPlace(stack)) continue;
                ItemStack ts = target.getItem();
                if (ts.isEmpty()) { int n = Math.min(stack.getCount(), target.getMaxStackSize(stack)); target.set(stack.split(n)); target.setChanged(); }
                else if (ItemStack.isSameItemSameTags(stack, ts)) { int space = target.getMaxStackSize(stack) - ts.getCount(); if (space > 0) { int n = Math.min(stack.getCount(), space); ts.grow(n); stack.shrink(n); target.set(ts); target.setChanged(); } }
            }
            if (stack.isEmpty()) { slot.setChanged(); return result; }
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
        cleanSlotsFromContainer(toStorage, beyond$repairSlots, new int[]{0, 1});
        // 结果槽对齐原版 GrindstoneMenu.removed：关闭时直接丢弃，不归还
        beyond$resultSlots.setItem(0, ItemStack.EMPTY);
    }
}
