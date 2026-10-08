package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量附魔工作站菜单（第 8 种网络工作站）：
 * 放入单件可附魔装备；<b>候选列表与显示由客户端基于装备可附魔列表 + 客户端网络视图构建</b>，
 * 服务端只接受提交的合并操作。
 *
 * 合并规则：
 * <ul>
 *   <li>所选等级 S 有对应等级书 → 直接使用，按附魔分离器公式计费；</li>
 *   <li>无 S 级但存在更高等级书 → 取比 S 高且最接近的等级 L 拆分（折半递归等价结果：
 *       回存 (L-1)..S 各一本，S 级用于本次附魔）；每步拆分按配置消耗普通书（默认保留原载体 1 本/步，
 *       配置开启原载体消耗则 2 本/步）与经验（默认 0）；</li>
 *   <li>取高规则写回装备（装备留在界面槽）。</li>
 * </ul>
 */
public class DimensionsEnchantMergeMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    private static final int WEQ_X = 8;
    private static final int WEQ_Y = 10;

    protected final Container beyond$mergeSlots;
    protected int beyond$wsS = -1;

    // 客户端构造
    public DimensionsEnchantMergeMenu(int id, Inventory inv, FriendlyByteBuf b) {
        this(ModMenus.ENCHANT_MERGE.get(), id, inv,
                new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE));
    }

    // 主构造
    public DimensionsEnchantMergeMenu(MenuType<?> t, int id, Inventory inv, AbstractUnorderedStackHandler d) {
        super(t, id, inv, d);
        this.beyond$mergeSlots = new SimpleContainer(1) {
            @Override public void setChanged() {
                super.setChanged();
                DimensionsEnchantMergeMenu.this.slotsChanged(this);
            }
        };
        beyond$wsS = slots.size();
        addSlot(new Slot(beyond$mergeSlots, 0, WEQ_X, ey(WEQ_Y)) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack s) { return beyond$canMergeItem(s); }
        });
        customSlotIndices.add(slots.size() - 1);
    }

    /** 是否为可作为合并目标的装备：非附魔书、且物品本身可附魔（已附魔装备允许继续升级） */
    public static boolean beyond$canMergeItem(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.is(Items.ENCHANTED_BOOK)) return false;
        return s.getItem().isEnchantable(s) || !EnchantmentHelper.getEnchantmentsForCrafting(s).isEmpty();
    }

    private static boolean beyond$applicable(Holder<Enchantment> holder, ItemStack item) {
        return item.supportsEnchantment(holder);
    }

    private static boolean beyond$conflicts(Holder<Enchantment> holder, ItemEnchantments existing) {
        for (Holder<Enchantment> h : existing.keySet()) if (!Enchantment.areCompatible(h, holder)) return true;
        return false;
    }

    /** 扫描网络中的单附魔书：按附魔聚合各等级的书键与数量 */
    private static Map<Holder<Enchantment>, Map<Integer, BookRef>> beyond$scanAllBooks(AbstractUnorderedStackHandler storage) {
        Map<Holder<Enchantment>, Map<Integer, BookRef>> map = new HashMap<>();
        if (storage == null) return map;
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack s = ik.getReadOnlyStack();
            if (!s.is(Items.ENCHANTED_BOOK)) continue;
            ItemEnchantments stored = s.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored == null || stored.size() != 1) continue; // 仅单附魔书
            Holder<Enchantment> holder = stored.keySet().iterator().next();
            int lv = stored.getLevel(holder);
            if (lv <= 0) continue;
            Map<Integer, BookRef> byLevel = map.computeIfAbsent(holder, k -> new HashMap<>());
            BookRef prev = byLevel.get(lv);
            if (prev == null) byLevel.put(lv, new BookRef(lv, ik, ka.amount()));
            else byLevel.put(lv, new BookRef(lv, prev.key(), prev.count() + ka.amount()));
        }
        return map;
    }

    /**
     * 服务端执行合并：按提交的 (附魔, 等级) 列表应用目标状态——
     * 等级 0 = 清除该附魔；低于已有 = 降级；高于已有 = 升级。
     * 清除/降级免费且不消耗、不返还附魔书；升级消耗单附魔书 + 网络 XP（必要时拆分高等级书）。
     */
    public void doMerge(ServerPlayer sp, List<Holder<Enchantment>> holders, List<Integer> levels) {
        if (sp.level().isClientSide()) return;
        if (!CommandConfig.enchantMergeEnable()) return;
        ItemStack item = beyond$mergeSlots.getItem(0);
        if (item.isEmpty() || !beyond$canMergeItem(item)) return;
        AbstractUnorderedStackHandler storage = this.storage;
        if (storage == null) return;

        Map<Holder<Enchantment>, Map<Integer, BookRef>> books = beyond$scanAllBooks(storage);
        ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(item);
        boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
        boolean assumeAll = CommandConfig.enchantMergeAssumeAll();
        boolean keepRemoved = CommandConfig.enchantMergeKeepRemovedBooks();
        long extraPerEnchant = CommandConfig.enchantMergeExtraCostPerEnchant();
        long assumeExtra = CommandConfig.enchantMergeAssumeAllExtraCost();
        // 按提交（启用）顺序收集附魔；未提及的已有附魔追加保留；最终按等级降序写入（同级保持启用顺序）
        Map<Holder<Enchantment>, Integer> ordered = new java.util.LinkedHashMap<>();
        java.util.Set<Holder<Enchantment>> cleared = new java.util.HashSet<>();
        List<Refund> refunds = new ArrayList<>();
        List<Plan> plans = new ArrayList<>();
        long totalXp = 0;
        int splitSteps = 0;
        boolean changed = false;
        for (int i = 0; i < holders.size() && i < levels.size(); i++) {
            Holder<Enchantment> holder = holders.get(i);
            if (holder == null) continue;
            int cur = existing.getLevel(holder);
            int chosen = levels.get(i);
            if (chosen <= 0) {
                if (cur > 0) {
                    cleared.add(holder);
                    changed = true;
                    if (keepRemoved) refunds.add(new Refund(holder, cur)); // 清除：返还原等级书
                }
                continue;
            }
            if (chosen == cur) { ordered.put(holder, cur); continue; }  // 不变（仍记录）
            if (chosen < cur) {
                ordered.put(holder, chosen);
                changed = true;
                if (keepRemoved) refunds.add(new Refund(holder, cur - chosen)); // 降级：返还差值书
                continue;
            }
            // 升级
            Map<Integer, BookRef> byLevel = books.get(holder);
            boolean hasBook = byLevel != null && !byLevel.isEmpty();
            if (!hasBook && !assumeAll) continue; // 无书且未开启“视为拥有”
            int maxLevel;
            if (hasBook) {
                maxLevel = 0;
                for (int lv : byLevel.keySet()) if (lv > maxLevel) maxLevel = lv;
                if (cur > maxLevel) maxLevel = cur;
            } else {
                maxLevel = holder.value().getMaxLevel();
            }
            chosen = Mth.clamp(chosen, 1, maxLevel);
            if (chosen <= cur) continue;
            if (!beyond$applicable(holder, item)) continue;                // 不适用
            if (cur == 0 && checkConflict && beyond$conflicts(holder, existing)) continue; // 冲突
            ordered.put(holder, chosen);
            changed = true;
            long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                    List.of(new EnchantmentBookSeparatorHandler.Entry(holder, chosen)), 1);
            if (!hasBook) {
                // 视为拥有：不消耗书，按高额经验计费
                long cost = mergeCost + assumeExtra + extraPerEnchant;
                plans.add(new Plan(holder, chosen, chosen, null, cost, 0, true));
                totalXp += cost;
                continue;
            }
            BookRef exact = byLevel.get(chosen);
            if (exact != null) {
                long cost = mergeCost + extraPerEnchant;
                plans.add(new Plan(holder, chosen, chosen, exact.key(), cost, 0, false));
                totalXp += cost;
            } else {
                int from = 0;
                for (int lv : byLevel.keySet()) if (lv > chosen && (from == 0 || lv < from)) from = lv;
                if (from <= chosen) continue;
                BookRef src = byLevel.get(from);
                int steps = from - chosen;
                long cost = mergeCost + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L + extraPerEnchant;
                plans.add(new Plan(holder, chosen, chosen, src.key(), cost, from, false));
                totalXp += cost;
                splitSteps += steps;
            }
        }
        // 追加未被提交提及且未清除的已有附魔（保留）
        for (Holder<Enchantment> h : existing.keySet()) {
            if (!ordered.containsKey(h) && !cleared.contains(h)) {
                ordered.put(h, existing.getLevel(h));
            }
        }
        if (plans.isEmpty() && !changed) return; // 无任何变化
        totalXp = beyond$scaleCost(totalXp); // 统一应用倍率 / 百分比加成

        int bookNeed = splitSteps * (CommandConfig.enchantMergeConsumeBook() ? 2 : 1);
        if (!plans.isEmpty()) {
            // 预检：经验流体 / 每本升级书 / 拆分所需普通书
            long netXp = storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
            if (netXp < totalXp) {
                sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant_merge.need_xp"));
                return;
            }
            for (Plan p : plans) {
                if (!p.noBook() && storage.extract(p.key(), 1, true, false).amount() < 1) return;
            }
            if (bookNeed > 0) {
                long haveBooks = storage.getStackByKey(new ItemStackKey(new ItemStack(Items.BOOK))).amount();
                if (haveBooks < bookNeed) {
                    sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant_merge.need_book", bookNeed));
                    return;
                }
            }
            // 执行升级：扣 XP / 普通书 → 逐本扣书并产出拆分书
            if (totalXp > 0) storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), totalXp, false, false);
            if (bookNeed > 0) storage.extract(new ItemStackKey(new ItemStack(Items.BOOK)), bookNeed, false, false);
            for (Plan p : plans) {
                if (p.noBook()) continue; // 视为拥有：不消耗书
                if (storage.extract(p.key(), 1, false, false).amount() < 1) {
                    // 并发兜底：扣书失败则该升级项回退为原等级
                    ordered.put(p.holder(), existing.getLevel(p.holder()));
                    continue;
                }
                if (p.splitFrom() > p.chosenLevel()) {
                    for (int lv = p.splitFrom() - 1; lv >= p.chosenLevel(); lv--) {
                        beyond$insertBook(storage, p.holder(), lv);
                    }
                }
            }
        }

        // 保留清除/降级移除的附魔为附魔书放入网络
        for (Refund r : refunds) beyond$insertBook(storage, r.holder(), r.level());

        // 应用目标状态（支持清除/降级/升级）；按等级降序写入，同级保持启用顺序
        ItemStack result = item.copy();
        List<Map.Entry<Holder<Enchantment>, Integer>> merged = new ArrayList<>(ordered.entrySet());
        merged.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        ItemEnchantments.Mutable orderedMut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Map.Entry<Holder<Enchantment>, Integer> en : merged) orderedMut.set(en.getKey(), en.getValue());
        EnchantmentHelper.setEnchantments(result, orderedMut.toImmutable());
        beyond$mergeSlots.setItem(0, result);
        sp.awardStat(net.minecraft.stats.Stats.ENCHANT_ITEM);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                1.0F, sp.level().random.nextFloat() * 0.1F + 0.9F);
        beyond$mergeSlots.setChanged();
    }

    public void handleSubmit(ServerPlayer sp, List<Holder<Enchantment>> holders, List<Integer> levels) {
        doMerge(sp, holders, levels);
    }

    // ── 候选/计划数据结构 ──
    /** 网络单附魔书某等级的引用 */
    private record BookRef(int level, ItemStackKey key, long count) {}
    /** 清除/降级移除的附魔（组装成书返还，level = 返还等级） */
    private record Refund(Holder<Enchantment> holder, int level) {}
    /** 一次合并计划项：splitFrom 为拆分来源等级（0 = 直接使用所选等级书）；noBook = 视为拥有（不消耗书） */
    private record Plan(Holder<Enchantment> holder, int resultLevel, int chosenLevel, ItemStackKey key, long cost, int splitFrom, boolean noBook) {}

    /** 费用缩放：统一乘倍率与百分比加成（结果向下取整）。 */
    private static long beyond$scaleCost(long base) {
        double mult = CommandConfig.enchantMergeCostMultiplier();
        int pct = CommandConfig.enchantMergeCostPercentBonus();
        return Math.max(0L, Math.round(base * mult * (1.0D + pct / 100.0D)));
    }

    /** 把指定附魔/等级的单附魔书放入网络存储。 */
    private static void beyond$insertBook(AbstractUnorderedStackHandler storage, Holder<Enchantment> holder, int level) {
        if (storage == null || holder == null || level <= 0) return;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mut.set(holder, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        storage.insert(new ItemStackKey(book), 1, false);
    }

    // ── BI 公开 API（GUI 使用）──
    public ItemStack getInput() { return beyond$mergeSlots.getItem(0); }

    // ── 槽位/布局 ──
    /** 面板高度随客户端候选行数对齐（行区起点 35、行高 16、底部 3+12 = 15）。 */
    @Override public int getPanelHeight() {
        int rows;
        try { rows = com.solr98.beyondintegration.ClientConfig.enchantMergeRows(); }
        catch (Throwable t) { rows = 5; }
        rows = Math.min(10, Math.max(1, rows));
        return 30 + rows * 16 + 20;
    }
    /** 附魔合并界面移除面板与物品栏之间的连接分隔条。 */
    @Override public int connectionSeparatorHeight() { return 0; }

    @Override public void rebuildSlots() {
        super.rebuildSlots();
        if (beyond$wsS >= 0) {
            setSlotX(slots.get(beyond$wsS), WEQ_X);
            setSlotY(slots.get(beyond$wsS), ey(WEQ_Y));
        }
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        // 候选与显示由客户端基于装备 + 客户端网络视图构建，服务端只负责执行提交的合并操作
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (slotIndex == beyond$wsS) {
            if (!moveStackTo(stack, inventoryStartIndex, inventoryEndIndex, true)) return ItemStack.EMPTY;
            if (!stack.isEmpty() && this.storage != null) {
                long remaining = this.storage.insert(new ItemStackKey(stack), stack.getCount(), false).amount();
                stack.setCount((int) remaining);
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return result;
        }
        if (slotIndex >= inventoryStartIndex && slotIndex < inventoryEndIndex) {
            Slot target = this.slots.get(beyond$wsS);
            if (target.mayPlace(stack)) {
                ItemStack ts = target.getItem();
                if (ts.isEmpty()) {
                    target.set(stack.split(1));
                    target.setChanged();
                    slot.setChanged();
                    return result;
                } else if (ItemStack.isSameItemSameComponents(stack, ts)) {
                    int space = target.getMaxStackSize(stack) - ts.getCount();
                    if (space > 0) { int n = Math.min(stack.getCount(), space); ts.grow(n); stack.shrink(n); target.set(ts); target.setChanged(); }
                    if (stack.isEmpty()) slot.setChanged();
                    return result;
                }
            }
        }
        return super.quickMoveStack(player, slotIndex);
    }

    @Override public void removed(@NotNull Player p) {
        super.removed(p);
        if (p.level().isClientSide()) return;
        cleanSlotsFromContainer(firstCraftReturnDir, beyond$mergeSlots, new int[]{0});
    }

    @Override public void cleanSlots(boolean toStorage) {
        cleanSlotsFromContainer(toStorage, beyond$mergeSlots, new int[]{0});
    }
}
