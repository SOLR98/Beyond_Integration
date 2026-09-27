package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.EnchantMergeListPayload;
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
 * 放入单件可附魔装备，服务端扫描网络中的<b>单附魔书</b>，列出该装备可附加/升级的附魔候选
 * （过滤适用性、冲突、无可提升项），下发客户端由玩家勾选并调整等级。
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
    private static final int WEQ_Y = 14;

    protected final Container beyond$mergeSlots;
    protected int beyond$wsS = -1;

    /** 可合并附魔候选（服务端计算下发；客户端由 EnchantMergeListPayload 镜像） */
    public final List<MergeOption> options = new ArrayList<>();

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
        if (!player.level().isClientSide()) beyond$recalc();
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

    /** 服务端重算候选并下发（装备变化时调用） */
    private void beyond$recalc() {
        if (player.level().isClientSide()) return;
        List<MergeOption> out = new ArrayList<>();
        ItemStack item = beyond$mergeSlots.getItem(0);
        if (!item.isEmpty() && beyond$canMergeItem(item) && CommandConfig.enchantMergeEnable()) {
            Map<Holder<Enchantment>, Map<Integer, BookRef>> books = beyond$scanAllBooks(this.storage);
            if (!books.isEmpty()) {
                ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(item);
                boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
                for (Map.Entry<Holder<Enchantment>, Map<Integer, BookRef>> entry : books.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    Map<Integer, BookRef> byLevel = entry.getValue();
                    if (byLevel.isEmpty()) continue;
                    int maxLevel = 0;
                    long levelMask = 0;
                    for (Map.Entry<Integer, BookRef> le : byLevel.entrySet()) {
                        int lv = le.getKey();
                        if (lv > maxLevel) maxLevel = lv;
                        if (lv >= 1 && lv <= 63) levelMask |= 1L << (lv - 1);
                    }
                    int cur = existing.getLevel(holder);
                    if (cur > 0 && maxLevel <= cur) continue;
                    if (!beyond$applicable(holder, item)) continue;
                    if (cur == 0 && !ignoreConflict && beyond$conflicts(holder, existing)) continue;
                    BookRef top = byLevel.get(maxLevel);
                    out.add(new MergeOption(holder, maxLevel,
                            (int) Math.min(Integer.MAX_VALUE, top.count()), cur, levelMask));
                }
            }
        }
        options.clear();
        options.addAll(out);
        if (player instanceof ServerPlayer sp) {
            PacketHandler.sendToPlayer(sp, new EnchantMergeListPayload(containerId, out));
        }
    }

    /** 客户端接收候选列表 */
    public void acceptOptions(List<MergeOption> list) {
        options.clear();
        if (list != null) options.addAll(list);
    }

    /** 服务端执行合并：所选等级书优先；否则拆分最接近的更高等级书（每步消耗普通书/经验按配置） */
    public void doMerge(ServerPlayer sp, List<Holder<Enchantment>> holders, List<Integer> levels) {
        if (sp.level().isClientSide()) return;
        if (!CommandConfig.enchantMergeEnable()) return;
        ItemStack item = beyond$mergeSlots.getItem(0);
        if (item.isEmpty() || !beyond$canMergeItem(item)) return;
        AbstractUnorderedStackHandler storage = this.storage;
        if (storage == null) return;

        Map<Holder<Enchantment>, Map<Integer, BookRef>> books = beyond$scanAllBooks(storage);
        ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(item);
        boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
        List<Plan> plans = new ArrayList<>();
        long totalXp = 0;
        int splitSteps = 0;
        for (int i = 0; i < holders.size() && i < levels.size(); i++) {
            Holder<Enchantment> holder = holders.get(i);
            if (holder == null) continue;
            Map<Integer, BookRef> byLevel = books.get(holder);
            if (byLevel == null || byLevel.isEmpty()) continue;
            int maxLevel = 0;
            for (int lv : byLevel.keySet()) if (lv > maxLevel) maxLevel = lv;
            int chosen = Mth.clamp(levels.get(i), 1, maxLevel);
            int cur = existing.getLevel(holder);
            int result = Math.max(cur, chosen);
            if (result <= cur) continue;
            if (!beyond$applicable(holder, item)) continue;
            if (cur == 0 && !ignoreConflict && beyond$conflicts(holder, existing)) continue;
            BookRef exact = byLevel.get(chosen);
            if (exact != null) {
                long cost = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentBookSeparatorHandler.Entry(holder, chosen)), 1);
                plans.add(new Plan(holder, result, chosen, exact.key(), cost, 0));
                totalXp += cost;
            } else {
                int from = 0;
                for (int lv : byLevel.keySet()) if (lv > chosen && (from == 0 || lv < from)) from = lv;
                if (from <= chosen) continue;
                BookRef src = byLevel.get(from);
                int steps = from - chosen;
                long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentBookSeparatorHandler.Entry(holder, chosen)), 1); // 合并到装备仍按公式计费
                long cost = mergeCost + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L;
                plans.add(new Plan(holder, result, chosen, src.key(), cost, from));
                totalXp += cost;
                splitSteps += steps;
            }
        }
        if (plans.isEmpty()) return;

        // 预检：经验流体 / 每本书 / 拆分所需普通书
        long netXp = storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
        if (netXp < totalXp) {
            sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant_merge.need_xp"));
            return;
        }
        for (Plan p : plans) {
            if (storage.extract(p.key(), 1, true, false).amount() < 1) return;
        }
        int bookNeed = splitSteps * (CommandConfig.enchantMergeConsumeBook() ? 2 : 1);
        if (bookNeed > 0) {
            long haveBooks = storage.getStackByKey(new ItemStackKey(new ItemStack(Items.BOOK))).amount();
            if (haveBooks < bookNeed) {
                sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant_merge.need_book", bookNeed));
                return;
            }
        }

        // 执行：扣 XP / 普通书 → 逐本扣书、拆分产出并施加附魔
        if (totalXp > 0) storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), totalXp, false, false);
        if (bookNeed > 0) storage.extract(new ItemStackKey(new ItemStack(Items.BOOK)), bookNeed, false, false);
        ItemStack result = item.copy();
        for (Plan p : plans) {
            if (storage.extract(p.key(), 1, false, false).amount() < 1) continue;
            if (p.splitFrom() > p.chosenLevel()) {
                for (int lv = p.splitFrom() - 1; lv >= p.chosenLevel(); lv--) {
                    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                    ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mut.set(p.holder(), lv);
                    book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
                    storage.insert(new ItemStackKey(book), 1, false);
                }
            }
            ItemEnchantments cur = EnchantmentHelper.getEnchantmentsForCrafting(result);
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(cur);
            mut.set(p.holder(), p.resultLevel());
            EnchantmentHelper.setEnchantments(result, mut.toImmutable());
        }
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
    /** 可合并附魔候选：附魔 Holder / 网络最高可合并等级 / 该等级书数量 / 装备已有等级 / 有库存等级位掩码（bit i = i+1 级） */
    public record MergeOption(Holder<Enchantment> holder, int maxLevel, int stock, int existing, long levelMask) {}
    /** 网络单附魔书某等级的引用 */
    private record BookRef(int level, ItemStackKey key, long count) {}
    /** 一次合并计划项：resultLevel 为写入装备的最终等级；splitFrom 为拆分来源等级（0 = 直接使用所选等级书，不拆分） */
    private record Plan(Holder<Enchantment> holder, int resultLevel, int chosenLevel, ItemStackKey key, long cost, int splitFrom) {}

    // ── BI 公开 API（GUI 使用）──
    public ItemStack getInput() { return beyond$mergeSlots.getItem(0); }

    // ── 槽位/布局 ──
    @Override public int getPanelHeight() { return 150; }

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
        if (inventory == this.beyond$mergeSlots) beyond$recalc();
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
