package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.init.ModMenus;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.solr98.beyondintegration.network.EnchantMergeListPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量附魔工作站菜单（第 8 种网络工作站）：
 * 放入单件可附魔装备，服务端扫描网络中的<b>单附魔书</b>，列出该装备可附加/升级的附魔候选
 * （过滤适用性、冲突、无可提升项），下发客户端由玩家勾选并调整等级；
 * 提交后消耗对应单附魔书 + 网络 XP 流体，按"取高"规则合并到装备（装备留在界面槽）。
 *
 * 合并规则复用现有宽松配置：enchantIgnoreConflict（冲突是否放行）；
 * 费用复用附魔分离器的公式与网络 XP 流体（EnchantmentBookSeparatorHandler.calcCost / xpFluidKey）。
 */
public class DimensionsEnchantMergeMenu extends DimensionsStorageMenu implements ICleanableWorkstation {
    // 工作台装备槽坐标（相对 GUI）：面板内 (8,15)
    private static final int WEQ_X = 8;
    private static final int WEQ_Y = 14;

    /** 装备输入槽（1 格） */
    protected final Container beyond$mergeSlots;
    /** 本菜单自加槽位起始索引 */
    protected int beyond$wsS = -1;

    /** 可合并附魔候选（服务端计算下发；客户端由 EnchantMergeListPacket 镜像） */
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
        // 服务端：初始化候选（此时通常为空）
        if (!player.level().isClientSide()) beyond$recalc();
    }

    /** 是否为可作为合并目标的装备：非附魔书、且物品本身可附魔（已附魔装备允许继续升级） */
    public static boolean beyond$canMergeItem(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() instanceof EnchantedBookItem) return false;
        // 原版可附魔判定不含 isEnchanted 分支（已附魔装备仍可升级）
        return s.getItem().isEnchantable(s) || !EnchantmentHelper.getEnchantments(s).isEmpty();
    }

    /** 附魔是否可应用到该装备（适用性） */
    private static boolean beyond$applicable(Enchantment ench, ItemStack item) {
        return ench.canApplyAtEnchantingTable(item) || ench.canEnchant(item);
    }

    /** 该附魔是否与装备已有附魔冲突 */
    private static boolean beyond$conflicts(Enchantment ench, Map<Enchantment, Integer> existing) {
        for (Enchantment ex : existing.keySet()) if (!ench.isCompatibleWith(ex)) return true;
        return false;
    }

    /** 扫描网络中的单附魔书：按附魔聚合各等级的书键与数量 */
    private static Map<Enchantment, Map<Integer, BookRef>> beyond$scanAllBooks(AbstractUnorderedStackHandler storage) {
        Map<Enchantment, Map<Integer, BookRef>> map = new HashMap<>();
        if (storage == null) return map;
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack s = ik.getReadOnlyStack();
            if (!(s.getItem() instanceof EnchantedBookItem)) continue;
            List<EnchantmentInstance> list = EnchantmentBookSeparatorHandler.extractStoredEnchantments(s);
            if (list.size() != 1) continue; // 仅单附魔书
            EnchantmentInstance ei = list.get(0);
            if (ei.level <= 0) continue;
            Map<Integer, BookRef> byLevel = map.computeIfAbsent(ei.enchantment, k -> new HashMap<>());
            BookRef prev = byLevel.get(ei.level);
            if (prev == null) byLevel.put(ei.level, new BookRef(ei.level, ik, ka.amount()));
            else byLevel.put(ei.level, new BookRef(ei.level, prev.key(), prev.count() + ka.amount()));
        }
        return map;
    }

    /** 服务端重算候选并下发（装备变化时调用） */
    private void beyond$recalc() {
        if (player.level().isClientSide()) return;
        List<MergeOption> out = new ArrayList<>();
        ItemStack item = beyond$mergeSlots.getItem(0);
        if (!item.isEmpty() && beyond$canMergeItem(item)) {
            Map<Enchantment, Map<Integer, BookRef>> books = beyond$scanAllBooks(this.storage);
            if (!books.isEmpty()) {
                Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(item);
                boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
                for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {
                    if (!ench.isDiscoverable()) continue;
                    Map<Integer, BookRef> byLevel = books.get(ench);
                    if (byLevel == null || byLevel.isEmpty()) continue;
                    int maxLevel = 0;
                    long levelMask = 0;
                    for (Map.Entry<Integer, BookRef> le : byLevel.entrySet()) {
                        int lv = le.getKey();
                        if (lv > maxLevel) maxLevel = lv;
                        if (lv >= 1 && lv <= 63) levelMask |= 1L << (lv - 1);
                    }
                    int cur = existing.getOrDefault(ench, 0);
                    if (cur > 0 && maxLevel <= cur) continue; // 无可提升
                    if (!beyond$applicable(ench, item)) continue;
                    if (cur == 0 && !ignoreConflict && beyond$conflicts(ench, existing)) continue;
                    BookRef top = byLevel.get(maxLevel);
                    out.add(new MergeOption(BuiltInRegistries.ENCHANTMENT.getId(ench), maxLevel,
                            (int) Math.min(Integer.MAX_VALUE, top.count()), cur, levelMask));
                }
            }
        }
        options.clear();
        options.addAll(out);
        if (player instanceof ServerPlayer sp) {
            PacketHandler.sendToPlayer(sp, new EnchantMergeListPacket(containerId, out));
        }
    }

    /** 客户端接收候选列表 */
    public void acceptOptions(List<MergeOption> list) {
        options.clear();
        if (list != null) options.addAll(list);
    }

    /**
     * 服务端执行合并：按提交的 (附魔, 等级) 列表校验并消耗单附魔书 + 网络 XP 流体，
     * 取高规则写回装备。全部预检通过才执行（避免部分扣除）。
     */
    public void doMerge(ServerPlayer sp, int[] enchIds, int[] levels) {
        if (sp.level().isClientSide()) return;
        if (!CommandConfig.enchantMergeEnable()) return;
        ItemStack item = beyond$mergeSlots.getItem(0);
        if (item.isEmpty() || !beyond$canMergeItem(item)) return;
        AbstractUnorderedStackHandler storage = this.storage;
        if (storage == null) return;

        Map<Enchantment, Map<Integer, BookRef>> books = beyond$scanAllBooks(storage);
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(item);
        boolean ignoreConflict = CommandConfig.enchantIgnoreConflict();
        List<Plan> plans = new ArrayList<>();
        long totalXp = 0;
        int splitCount = 0;
        for (int i = 0; i < enchIds.length && i < levels.length; i++) {
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(enchIds[i]);
            if (ench == null) continue;
            Map<Integer, BookRef> byLevel = books.get(ench);
            if (byLevel == null || byLevel.isEmpty()) continue;
            int maxLevel = 0;
            for (int lv : byLevel.keySet()) if (lv > maxLevel) maxLevel = lv;
            int chosen = Mth.clamp(levels[i], 1, maxLevel);
            int cur = existing.getOrDefault(ench, 0);
            int result = Math.max(cur, chosen);
            if (result <= cur) continue;                     // 无提升
            if (!beyond$applicable(ench, item)) continue;    // 不适用
            if (cur == 0 && !ignoreConflict && beyond$conflicts(ench, existing)) continue; // 冲突
            BookRef exact = byLevel.get(chosen);
            if (exact != null) {
                long cost = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, chosen)), 1);
                plans.add(new Plan(ench, result, chosen, exact.key(), cost, 0));
                totalXp += cost;
            } else {
                // 高等级书拆分：优先取比所选等级高且最接近的等级
                int from = 0;
                for (int lv : byLevel.keySet()) if (lv > chosen && (from == 0 || lv < from)) from = lv;
                if (from <= chosen) continue;
                BookRef src = byLevel.get(from);
                int steps = from - chosen; // 折半递归步数（每步消耗 1 或 2 本普通书）
                long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, chosen)), 1); // 合并到装备仍按公式计费
                long cost = mergeCost + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L;
                plans.add(new Plan(ench, result, chosen, src.key(), cost, from));
                totalXp += cost;
                splitCount += steps;
            }
        }
        if (plans.isEmpty()) return;

        // 预检：网络 XP / 每本书 / 拆分所需普通书是否充足（全部通过才执行）
        long netXp = storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
        if (netXp < totalXp) {
            sp.sendSystemMessage(Component.translatable("gui.beyond_integration.enchant_merge.need_xp"));
            return;
        }
        for (Plan p : plans) {
            if (storage.extract(p.key(), 1, true, false).amount() < 1) return;
        }
        int bookNeed = splitCount * (CommandConfig.enchantMergeConsumeBook() ? 2 : 1);
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
                // 折半递归的等价结果：回存 (from-1)..chosen 各一本（chosen 级另有一本用于本次附魔）
                for (int lv = p.splitFrom() - 1; lv >= p.chosenLevel(); lv--) {
                    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                    EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(p.ench(), lv));
                    storage.insert(new ItemStackKey(book), 1, false);
                }
            }
            result.enchant(p.ench(), p.resultLevel());
        }
        beyond$mergeSlots.setItem(0, result);
        sp.awardStat(net.minecraft.stats.Stats.ENCHANT_ITEM);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                1.0F, sp.level().random.nextFloat() * 0.1F + 0.9F);
        beyond$mergeSlots.setChanged(); // 触发重算候选
    }

    /** 提交合并（由 C2S 包调用） */
    public void handleSubmit(ServerPlayer sp, int[] enchIds, int[] levels) {
        doMerge(sp, enchIds, levels);
    }

    // ── 候选/计划数据结构 ──
    /** 可合并附魔候选：附魔注册 id / 网络最高可合并等级 / 该等级书数量 / 装备已有等级 / 有库存等级位掩码（bit i = i+1 级） */
    public record MergeOption(int enchId, int maxLevel, int stock, int existing, long levelMask) {}
    /** 网络单附魔书某等级的引用 */
    private record BookRef(int level, ItemStackKey key, long count) {}
    /** 一次合并计划项：resultLevel 为写入装备的最终等级；splitFrom 为拆分来源等级（0 = 直接使用所选等级书，不拆分） */
    private record Plan(Enchantment ench, int resultLevel, int chosenLevel, ItemStackKey key, long cost, int splitFrom) {}

    // ── BI 公开 API（GUI 使用）──
    /** 装备输入槽当前物品 */
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
    public void slotsChanged(Container inv) {
        super.slotsChanged(inv);
        if (inv == this.beyond$mergeSlots) beyond$recalc();
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
                } else if (ItemStack.isSameItemSameTags(stack, ts)) {
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
