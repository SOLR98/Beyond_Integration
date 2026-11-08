package com.solr98.beyondintegration.feature.enchant;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络内「附魔合并」的服务端逻辑（BdPage 版：没有实体槽，装备来自网络）。
 * <p>规则与 0.7.30 的 {@code DimensionsEnchantMergeMenu.doMerge} 一致：
 * <ul>
 *   <li>扫描网络里的<b>单附魔书</b>，按附魔聚合各等级的书键与数量；</li>
 *   <li>装备是网络里的一件物品（提交时按精确键取出、改完回插）；</li>
 *   <li>升级需要对应等级的书（不足时拆更高级的书，消耗普通书 + 拆分 XP）；</li>
 *   <li>费用复用 {@link EnchantmentBookSeparatorHandler#calcCost} + 每项额外费用 + 倍率/百分比；
 *       全部预检通过才执行，避免部分扣除。</li>
 * </ul>
 * 装备上的已有附魔会保留；提交里 level=0 表示清除、低于当前等级表示降级（保留配置可返还书）。
 * 页面侧只做展示与选择，权威计算全在服务端。
 */
public final class EnchantMergeService {

    private EnchantMergeService() {
    }

    /** 候选：一件装备上可升级的附魔。{@code assume}=网络里没有对应书但配置允许“视为全有”。 */
    public record Candidate(int enchId, int current, int max, long books, long cost, boolean assume) {
    }

    /** 提交项：目标等级（0 = 清除）。 */
    public record Selection(int enchId, int level) {
    }

    /** 合并结果。 */
    public record Outcome(boolean ok, String messageKey, long xpCost) {
    }

    private record BookRef(int level, ItemStackKey key, long count) {
    }

    private record Plan(Enchantment ench, int level, ItemStackKey key, long cost, int splitFrom, boolean noBook) {
    }

    private record Refund(Enchantment ench, int level) {
    }

    /** 是否可作为合并目标的装备：非附魔书、且物品本身可附魔（已附魔装备允许继续升级）。 */
    public static boolean canMerge(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() instanceof EnchantedBookItem) return false;
        return s.getItem().isEnchantable(s) || !EnchantmentHelper.getEnchantments(s).isEmpty();
    }

    /** 扫描该装备可升级的附魔候选（只读，不修改网络）。 */
    public static List<Candidate> scan(DimensionsNet net, ItemStack item) {
        List<Candidate> out = new ArrayList<>();
        if (net == null || !canMerge(item)) return out;
        Map<Enchantment, Map<Integer, BookRef>> books = scanAllBooks(net.getUnifiedStorage());
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(item);
        boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
        boolean assumeAll = CommandConfig.enchantMergeAssumeAll();
        long extraPerEnchant = CommandConfig.enchantMergeExtraCostPerEnchant();
        long assumeExtra = CommandConfig.enchantMergeAssumeAllExtraCost();

        for (Map.Entry<Enchantment, Map<Integer, BookRef>> entry : books.entrySet()) {
            Enchantment ench = entry.getKey();
            int cur = existing.getOrDefault(ench, 0);
            int maxLevel = cur;
            long totalBooks = 0;
            for (Map.Entry<Integer, BookRef> byLevel : entry.getValue().entrySet()) {
                if (byLevel.getKey() > maxLevel) maxLevel = byLevel.getKey();
                totalBooks += byLevel.getValue().count();
            }
            if (maxLevel <= cur) continue;
            if (!applicable(ench, item)) continue;
            if (cur == 0 && checkConflict && conflicts(ench, existing)) continue;
            long cost = EnchantmentBookSeparatorHandler.calcCost(
                    List.of(new EnchantmentInstance(ench, maxLevel)), 1) + extraPerEnchant;
            out.add(new Candidate(BuiltInRegistries.ENCHANTMENT.getId(ench), cur, maxLevel, totalBooks, cost, false));
        }
        if (assumeAll) {
            for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {
                if (books.containsKey(ench)) continue;
                int cur = existing.getOrDefault(ench, 0);
                int maxLevel = ench.getMaxLevel();
                if (maxLevel <= cur) continue;
                if (!applicable(ench, item)) continue;
                if (cur == 0 && checkConflict && conflicts(ench, existing)) continue;
                long cost = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentInstance(ench, maxLevel)), 1) + assumeExtra + extraPerEnchant;
                out.add(new Candidate(BuiltInRegistries.ENCHANTMENT.getId(ench), cur, maxLevel, 0, cost, true));
            }
        }
        return out;
    }

    /**
     * 执行合并：从网络取出装备 → 扣费/扣书 → 写附魔 → 回插网络。
     * 全部预检通过才动网络；失败时返回 {@code ok=false} 与提示键。
     */
    public static Outcome merge(DimensionsNet net, ItemStackKey equipmentKey, List<Selection> selections) {
        if (net == null || equipmentKey == null || equipmentKey.isEmpty()) {
            return new Outcome(false, "message.beyond_integration.enchant_merge.invalid", 0);
        }
        if (!CommandConfig.enchantMergeEnable()) {
            return new Outcome(false, "message.beyond_integration.enchant_merge.disabled", 0);
        }
        ItemStack item = equipmentKey.getReadOnlyStack().copy();
        if (!canMerge(item)) {
            return new Outcome(false, "message.beyond_integration.enchant_merge.invalid", 0);
        }
        IStackHandler storage = net.getUnifiedStorage();
        Map<Enchantment, Map<Integer, BookRef>> books = scanAllBooks(storage);
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(item);
        boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
        boolean keepRemoved = CommandConfig.enchantMergeKeepRemovedBooks();
        long extraPerEnchant = CommandConfig.enchantMergeExtraCostPerEnchant();
        long assumeExtra = CommandConfig.enchantMergeAssumeAllExtraCost();

        Map<Enchantment, Integer> ordered = new LinkedHashMap<>();
        List<Refund> refunds = new ArrayList<>();
        List<Plan> plans = new ArrayList<>();
        long totalXp = 0;
        int splitCount = 0;

        for (Selection sel : selections) {
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.byId(sel.enchId());
            if (ench == null) continue;
            int cur = existing.getOrDefault(ench, 0);
            int chosen = sel.level();
            if (chosen <= 0) {
                if (cur > 0) {
                    if (keepRemoved) refunds.add(new Refund(ench, cur));
                }
                continue;
            }
            if (chosen == cur) {
                ordered.put(ench, cur);
                continue;
            }
            if (chosen < cur) {
                ordered.put(ench, chosen);
                if (keepRemoved) refunds.add(new Refund(ench, cur - chosen));
                continue;
            }
            Map<Integer, BookRef> byLevel = books.get(ench);
            boolean hasBook = byLevel != null && !byLevel.isEmpty();
            if (!hasBook && !CommandConfig.enchantMergeAssumeAll()) continue;
            int maxLevel;
            if (hasBook) {
                maxLevel = 0;
                for (int lv : byLevel.keySet()) if (lv > maxLevel) maxLevel = lv;
                if (cur > maxLevel) maxLevel = cur;
            } else {
                maxLevel = ench.getMaxLevel();
            }
            chosen = Mth.clamp(chosen, 1, maxLevel);
            if (chosen <= cur) continue;
            if (!applicable(ench, item)) continue;
            if (cur == 0 && checkConflict && conflicts(ench, existing)) continue;
            ordered.put(ench, chosen);
            long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                    List.of(new EnchantmentInstance(ench, chosen)), 1);
            if (!hasBook) {
                long cost = mergeCost + assumeExtra + extraPerEnchant;
                plans.add(new Plan(ench, chosen, null, cost, 0, true));
                totalXp += cost;
                continue;
            }
            BookRef exact = byLevel.get(chosen);
            if (exact != null) {
                long cost = mergeCost + extraPerEnchant;
                plans.add(new Plan(ench, chosen, exact.key(), cost, 0, false));
                totalXp += cost;
            } else {
                int from = 0;
                for (int lv : byLevel.keySet()) if (lv > chosen && (from == 0 || lv < from)) from = lv;
                if (from <= chosen) continue;
                BookRef src = byLevel.get(from);
                int steps = from - chosen;
                long cost = mergeCost + (long) CommandConfig.enchantMergeSplitXpCost() * steps * 20L + extraPerEnchant;
                plans.add(new Plan(ench, chosen, src.key(), cost, from, false));
                totalXp += cost;
                splitCount += steps;
            }
        }
        // 未提及且未清除的已有附魔保留
        for (Map.Entry<Enchantment, Integer> en : existing.entrySet()) {
            if (!ordered.containsKey(en.getKey())) ordered.put(en.getKey(), en.getValue());
        }
        if (plans.isEmpty() && ordered.equals(existing)) {
            return new Outcome(false, "message.beyond_integration.enchant_merge.no_change", 0);
        }
        totalXp = scaleCost(totalXp);
        int bookNeed = splitCount * (CommandConfig.enchantMergeConsumeBook() ? 2 : 1);

        // 预检：网络 XP / 每本升级书 / 拆分所需普通书
        if (!plans.isEmpty()) {
            long netXp = storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), Long.MAX_VALUE, true, false).amount();
            if (netXp < totalXp) {
                return new Outcome(false, "message.beyond_integration.enchant_merge.need_xp", totalXp);
            }
            for (Plan p : plans) {
                if (p.noBook() || p.key() == null) continue;
                if (storage.extract(p.key(), 1, true, false).amount() < 1) {
                    return new Outcome(false, "message.beyond_integration.enchant_merge.need_book_item", totalXp);
                }
            }
            if (bookNeed > 0) {
                long haveBooks = storage.getStackByKey(new ItemStackKey(new ItemStack(Items.BOOK))).amount();
                if (haveBooks < bookNeed) {
                    return new Outcome(false, "message.beyond_integration.enchant_merge.need_book", totalXp);
                }
            }
        }

        // 先取出装备（失败则不扣任何费用）
        KeyAmount taken = storage.extract(equipmentKey, 1, false, false);
        if (taken.isEmpty() || !(taken.key() instanceof ItemStackKey)) {
            return new Outcome(false, "message.beyond_integration.enchant_merge.missing_item", totalXp);
        }

        // 扣 XP / 普通书，逐本扣书并产出拆分书
        if (totalXp > 0) storage.extract(EnchantmentBookSeparatorHandler.xpFluidKey(), totalXp, false, false);
        if (bookNeed > 0) storage.extract(new ItemStackKey(new ItemStack(Items.BOOK)), bookNeed, false, false);
        for (Plan p : plans) {
            if (p.noBook() || p.key() == null) continue;
            if (storage.extract(p.key(), 1, false, false).amount() < 1) {
                ordered.put(p.ench(), existing.getOrDefault(p.ench(), 0));
                continue;
            }
            if (p.splitFrom() > p.level()) {
                for (int lv = p.splitFrom() - 1; lv >= p.level(); lv--) {
                    insertBook(storage, p.ench(), lv);
                }
            }
        }
        for (Refund r : refunds) insertBook(storage, r.ench(), r.level());

        // 写入附魔（按等级降序，同级保持顺序）
        List<Map.Entry<Enchantment, Integer>> merged = new ArrayList<>(ordered.entrySet());
        merged.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<Enchantment, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> en : merged) sorted.put(en.getKey(), en.getValue());
        ItemStack result = item.copy();
        EnchantmentHelper.setEnchantments(sorted, result);
        KeyAmount leftover = storage.insert(new ItemStackKey(result), result.getCount(), false);
        net.setDirty();
        return new Outcome(true, "message.beyond_integration.enchant_merge.done", totalXp);
    }

    // ───────────────────────── 内部 ─────────────────────────

    private static Map<Enchantment, Map<Integer, BookRef>> scanAllBooks(IStackHandler storage) {
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
            if (prev == null) {
                byLevel.put(ei.level, new BookRef(ei.level, ik, ka.amount()));
            } else {
                byLevel.put(ei.level, new BookRef(ei.level, prev.key(), prev.count() + ka.amount()));
            }
        }
        return map;
    }

    private static boolean applicable(Enchantment ench, ItemStack item) {
        return ench.canApplyAtEnchantingTable(item) || ench.canEnchant(item);
    }

    private static boolean conflicts(Enchantment ench, Map<Enchantment, Integer> existing) {
        for (Enchantment ex : existing.keySet()) if (!ench.isCompatibleWith(ex)) return true;
        return false;
    }

    private static long scaleCost(long base) {
        double mult = CommandConfig.enchantMergeCostMultiplier();
        int pct = CommandConfig.enchantMergeCostPercentBonus();
        return Math.max(0L, Math.round(base * mult * (1.0D + pct / 100.0D)));
    }

    private static void insertBook(IStackHandler storage, Enchantment ench, int level) {
        if (storage == null || ench == null || level <= 0) return;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(ench, level));
        storage.insert(new ItemStackKey(book), 1, false);
    }
}
