package com.solr98.beyondintegration.feature.enchant;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

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

    private record Plan(Holder<Enchantment> ench, int level, ItemStackKey key, long cost, int splitFrom, boolean noBook) {
    }

    private record Refund(Holder<Enchantment> ench, int level) {
    }

    /** 是否可作为合并目标的装备：非附魔书、且物品本身可附魔（已附魔装备允许继续升级）。 */
    public static boolean canMerge(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.is(Items.ENCHANTED_BOOK)) return false;
        return s.getItem().isEnchantable(s) || !EnchantmentHelper.getEnchantmentsForCrafting(s).isEmpty();
    }

    /** 扫描该装备可升级的附魔候选（只读，不修改网络）。 */
    public static List<Candidate> scan(DimensionsNet net, ItemStack item) {
        List<Candidate> out = new ArrayList<>();
        if (net == null || !canMerge(item)) return out;
        Map<Holder<Enchantment>, Map<Integer, BookRef>> books = scanAllBooks(net.getUnifiedStorage());
        Map<Holder<Enchantment>, Integer> existing = enchantmentsOf(item);
        boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
        boolean assumeAll = CommandConfig.enchantMergeAssumeAll();
        long extraPerEnchant = CommandConfig.enchantMergeExtraCostPerEnchant();
        long assumeExtra = CommandConfig.enchantMergeAssumeAllExtraCost();

        for (Map.Entry<Holder<Enchantment>, Map<Integer, BookRef>> entry : books.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
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
                    List.of(new EnchantmentBookSeparatorHandler.Entry(ench, maxLevel)), 1) + extraPerEnchant;
            out.add(new Candidate(enchId(ench), cur, maxLevel, totalBooks, cost, false));
        }
        if (assumeAll) {
            for (Holder<Enchantment> ench : allEnchants()) {
                if (books.containsKey(ench)) continue;
                int cur = existing.getOrDefault(ench, 0);
                int maxLevel = ench.value().getMaxLevel();
                if (maxLevel <= cur) continue;
                if (!applicable(ench, item)) continue;
                if (cur == 0 && checkConflict && conflicts(ench, existing)) continue;
                long cost = EnchantmentBookSeparatorHandler.calcCost(
                        List.of(new EnchantmentBookSeparatorHandler.Entry(ench, maxLevel)), 1) + assumeExtra + extraPerEnchant;
                out.add(new Candidate(enchId(ench), cur, maxLevel, 0, cost, true));
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
        Map<Holder<Enchantment>, Map<Integer, BookRef>> books = scanAllBooks(storage);
        Map<Holder<Enchantment>, Integer> existing = enchantmentsOf(item);
        boolean checkConflict = CommandConfig.enchantMergeCheckConflict();
        boolean keepRemoved = CommandConfig.enchantMergeKeepRemovedBooks();
        long extraPerEnchant = CommandConfig.enchantMergeExtraCostPerEnchant();
        long assumeExtra = CommandConfig.enchantMergeAssumeAllExtraCost();

        Map<Holder<Enchantment>, Integer> ordered = new LinkedHashMap<>();
        List<Refund> refunds = new ArrayList<>();
        List<Plan> plans = new ArrayList<>();
        long totalXp = 0;
        int splitCount = 0;

        for (Selection sel : selections) {
            Holder<Enchantment> ench = enchantById(sel.enchId());
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
                maxLevel = ench.value().getMaxLevel();
            }
            chosen = Mth.clamp(chosen, 1, maxLevel);
            if (chosen <= cur) continue;
            if (!applicable(ench, item)) continue;
            if (cur == 0 && checkConflict && conflicts(ench, existing)) continue;
            ordered.put(ench, chosen);
            long mergeCost = EnchantmentBookSeparatorHandler.calcCost(
                    List.of(new EnchantmentBookSeparatorHandler.Entry(ench, chosen)), 1);
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
        for (Map.Entry<Holder<Enchantment>, Integer> en : existing.entrySet()) {
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
        List<Map.Entry<Holder<Enchantment>, Integer>> merged = new ArrayList<>(ordered.entrySet());
        merged.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<Holder<Enchantment>, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<Holder<Enchantment>, Integer> en : merged) sorted.put(en.getKey(), en.getValue());
        ItemStack result = item.copy();
        ItemEnchantments.Mutable resultMut = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
        for (var en : sorted.entrySet()) resultMut.set(en.getKey(), en.getValue());
        EnchantmentHelper.setEnchantments(result, resultMut.toImmutable());
        KeyAmount leftover = storage.insert(new ItemStackKey(result), result.getCount(), false);
        net.setDirty();
        return new Outcome(true, "message.beyond_integration.enchant_merge.done", totalXp);
    }

    // ───────────────────────── 内部 ─────────────────────────

    private static Map<Holder<Enchantment>, Map<Integer, BookRef>> scanAllBooks(IStackHandler storage) {
        Map<Holder<Enchantment>, Map<Integer, BookRef>> map = new HashMap<>();
        if (storage == null) return map;
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack s = ik.getReadOnlyStack();
            if (!s.is(Items.ENCHANTED_BOOK)) continue;
            List<EnchantmentBookSeparatorHandler.Entry> list = EnchantmentBookSeparatorHandler.extractStoredEnchantments(s);
            if (list.size() != 1) continue; // 仅单附魔书
            EnchantmentBookSeparatorHandler.Entry ei = list.get(0);
            if (ei.level() <= 0) continue;
            Map<Integer, BookRef> byLevel = map.computeIfAbsent(ei.holder(), k -> new HashMap<>());
            BookRef prev = byLevel.get(ei.level());
            if (prev == null) {
                byLevel.put(ei.level(), new BookRef(ei.level(), ik, ka.amount()));
            } else {
                byLevel.put(ei.level(), new BookRef(ei.level(), prev.key(), prev.count() + ka.amount()));
            }
        }
        return map;
    }

    private static boolean applicable(Holder<Enchantment> ench, ItemStack item) {
        return item.supportsEnchantment(ench);
    }

    private static boolean conflicts(Holder<Enchantment> ench, Map<Holder<Enchantment>, Integer> existing) {
        for (Holder<Enchantment> ex : existing.keySet()) if (!Enchantment.areCompatible(ench, ex)) return true;
        return false;
    }

    private static long scaleCost(long base) {
        double mult = CommandConfig.enchantMergeCostMultiplier();
        int pct = CommandConfig.enchantMergeCostPercentBonus();
        return Math.max(0L, Math.round(base * mult * (1.0D + pct / 100.0D)));
    }

    private static void insertBook(IStackHandler storage, Holder<Enchantment> ench, int level) {
        if (storage == null || ench == null || level <= 0) return;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mut.set(ench, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
        storage.insert(new ItemStackKey(book), 1, false);
    }
    // ───────────────────── 1.21.1 附魔注册表辅助 ─────────────────────

    /** 取附魔注册表（1.21.1 附魔是 datapack 注册表；优先服务端，其次客户端世界）。 */
    private static net.minecraft.core.Registry<Enchantment> registry() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        }
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        }
        return null;
    }

    /** 数字 id → Holder（与 enchantClue 的下发口径一致）。 */
    private static Holder<Enchantment> enchantById(int id) {
        if (id < 0) return null;
        var reg = registry();
        if (reg == null) return null;
        return reg.asHolderIdMap().byId(id);
    }

    /** Holder → 数字 id（进入网络包时用）。 */
    private static int enchId(Holder<Enchantment> holder) {
        var reg = registry();
        if (reg == null) return -1;
        return reg.asHolderIdMap().getId(holder);
    }

    /** 物品当前附魔（Holder → 等级）。 */
    private static Map<Holder<Enchantment>, Integer> enchantmentsOf(ItemStack item) {
        ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(item);
        Map<Holder<Enchantment>, Integer> out = new LinkedHashMap<>();
        for (Holder<Enchantment> h : ench.keySet()) {
            out.put(h, ench.getLevel(h));
        }
        return out;
    }

    /** 全部附魔（「视为全有」模式用）。 */
    private static List<Holder<Enchantment>> allEnchants() {
        var reg = registry();
        if (reg == null) return List.of();
        List<Holder<Enchantment>> out = new ArrayList<>();
        reg.holders().forEach(out::add);
        return out;
    }
}