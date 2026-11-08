package com.solr98.beyondintegration.feature.enchant;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.InsertRule;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.StorageRequest;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 附魔分离规则：多附魔书进入网络时，按配置把附魔逐条分离为单一附魔书，
 * 并从网络中扣除经验（XP 流体）与空白书作为消耗。
 * <p>
 * 单附魔书可选"同类同级自动合并"：与网络内同附魔同等级的书 + 铁砧费用经验合成高一级书。
 * 合并采用<b>一次到位</b>：在 {@code plan} 里按网络存量算出可达的最高等级，
 * 一次 take/add 完成，保持事务原子性（不再靠"产出再入网"逐级级联）。
 * <p>
 * 支持自定义费用公式（FormulaParser）与网络级开关（EnchantSeparationAccessor）。
 */
public final class EnchantmentBookSeparatorHandler implements InsertRule
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation ID_SPLIT = new ResourceLocation("beyond_integration", "enchant_book_split");
    public static final ResourceLocation ID_MERGE = new ResourceLocation("beyond_integration", "enchant_book_merge");

    public static void register()
    {
        // 拆书与合并分两条规则注册：同一转换链上每条规则最多生效一次
        UnifiedStorageRules.insertMatching(ID_SPLIT, EnchantmentBookSeparatorHandler::matches, new EnchantmentBookSeparatorHandler());
        UnifiedStorageRules.insertMatching(ID_MERGE, EnchantmentBookSeparatorHandler::matches, new EnchantmentBookSeparatorHandler());
    }

    private static boolean matches(IStackKey<?> key)
    {
        if (!CommandConfig.enableEnchantmentSeparation()) return false;
        if (!(key instanceof ItemStackKey itemKey)) return false;
        return itemKey.getReadOnlyStack().getItem() instanceof EnchantedBookItem;
    }

    @Override
    public @Nullable InsertPlan plan(StorageRequest request)
    {
        if (!CommandConfig.enableEnchantmentSeparation()) return null;
        if (request.net() == null) return null;
        if (!(request.net() instanceof com.solr98.beyondintegration.handler.EnchantSeparationAccessor ea)
                || !ea.beyond$isEnchantSeparationEnabled()) return null;
        if (!(request.key() instanceof ItemStackKey itemKey)) return null;

        ItemStack itemStack = itemKey.getReadOnlyStack();
        if (itemStack.isEmpty()) return null;

        // 受保护物品（beyond_integration:protect_sep 标记）：跳过自动分离
        if (isProtected(itemStack)) return null;

        if (!(itemStack.getItem() instanceof EnchantedBookItem)) return null;

        List<EnchantmentInstance> ench = extractStoredEnchantments(itemStack);
        if (ench.size() <= 1)
        {
            // 单附魔书：可选的同类同级自动合并
            if (ench.size() == 1 && request.amount() == 1 && CommandConfig.enchantmentSeparationMergeSameLevel())
            {
                return planMerge(request, ench.get(0), itemKey);
            }
            return null;
        }

        if (CommandConfig.enchantmentSeparationDebug())
        {
            LOGGER.info("[enchant-sep] plan split book {} ({} enchants)", itemStack.getHoverName().getString(), ench.size());
        }
        return planSplit(ench);
    }

    // ── 分离单本多附魔书 ──

    private InsertPlan planSplit(List<EnchantmentInstance> ench)
    {
        long cost = calcCost(ench, 1);
        long booksNeeded = ench.size() - 1;

        InsertPlan plan = InsertPlan.convert(1);

        if (booksNeeded > 0)
        {
            plan.take(new ItemStackKey(new ItemStack(Items.BOOK)), booksNeeded);
        }
        if (cost > 0)
        {
            FluidStackKey xp = xpFluidKey();
            if (xp.isEmpty()) return null;
            plan.take(xp, cost);
        }

        for (EnchantmentInstance e : ench)
        {
            plan.add(singleBookKey(e.enchantment, e.level), 1);
        }
        return plan;
    }

    // ═══════════════════════════════════════════
    //  同类同级单附魔书自动合并（可选，默认关闭）
    // ═══════════════════════════════════════════

    /**
     * 一次到位合并：按网络现有存量把输入的单附魔书尽可能升级，
     * 每升一级消耗网络内一本同级书与铁砧合并费用（等级 → 经验点 → mB）。
     *
     * @return 计划；null 表示不处理（原书放行入库）
     */
    private InsertPlan planMerge(StorageRequest request, EnchantmentInstance e, ItemStackKey inputKey)
    {
        Enchantment ench = e.enchantment;
        int level = e.level;
        if (level <= 0) return null;

        int maxLevel = effectiveMaxLevel(ench);
        if (level >= maxLevel) return null;

        FluidStackKey xp = xpFluidKey();
        long xpAvailable = xp.isEmpty() ? 0L : request.stored(xp);

        int cur = level;
        long xpCostTotal = 0L;
        List<ItemStackKey> takenBooks = new ArrayList<>();

        while (cur < maxLevel)
        {
            ItemStackKey curKey = singleBookKey(ench, cur);
            // 该级网络存量（每次升级消耗一本；每级最多消耗一次）
            long available = request.stored(curKey);
            for (ItemStackKey k : takenBooks)
            {
                if (k.equals(curKey)) available--;
            }
            if (available < 1) break;

            long mb = xpTotalCost(anvilMergeCostLevel(ench, cur + 1)) * 20L;
            if (xpCostTotal + mb > xpAvailable) break;

            xpCostTotal += mb;
            takenBooks.add(curKey);
            cur++;
        }

        if (cur == level) return null;

        // 输入书本身不存入；产出最终等级的书，扣掉各级用掉的书与经验
        InsertPlan plan = InsertPlan.convert(1).add(singleBookKey(ench, cur), 1);
        for (ItemStackKey k : takenBooks)
        {
            plan.take(k, 1);
        }
        if (xpCostTotal > 0)
        {
            plan.take(xp, xpCostTotal);
        }

        if (CommandConfig.enchantmentSeparationDebug())
        {
            LOGGER.info("[enchant-sep] plan merge same-level {} {} -> {}", ench.getDescriptionId(), level, cur);
        }
        return plan;
    }

    /**
     * 附魔的有效等级上限。原版取 {@code getMaxLevel()}；
     * TODO 工作台迁移后接回 BI 自定义上限（原 DimensionsAnvilMenu.beyond$effectiveMaxLevel）。
     */
    private static int effectiveMaxLevel(Enchantment ench)
    {
        return Math.max(1, ench.getMaxLevel());
    }

    /** 构造单附魔书的精确存储键 */
    private static ItemStackKey singleBookKey(Enchantment ench, int level)
    {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(ench, level));
        return new ItemStackKey(book);
    }

    /** 原版铁砧合并两本附魔书的费用等级（同附魔同级取 +1；第二本为书时稀有度系数减半，至少 1） */
    private static int anvilMergeCostLevel(Enchantment ench, int resultLevel)
    {
        int k3 = switch (ench.getRarity())
        {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case VERY_RARE -> 8;
        };
        k3 = Math.max(1, k3 / 2);
        return k3 * resultLevel;
    }

    /** 原版等级→升级所需经验点（对齐 Player.getXpNeededForNextLevel） */
    private static int xpNeededForLevel(int level)
    {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** 从 0 升到指定等级所需的总经验点 */
    private static long xpTotalCost(int level)
    {
        long sum = 0;
        for (int l = 0; l < level; l++) sum += xpNeededForLevel(l);
        return sum;
    }

    // 以下为可被命令调用的手动分离逻辑
    // ═══════════════════════════════════════════════════════════════

    /** 对网络内所有多附魔书进行分离，返回处理摘要 */
    public static Component separateAll(DimensionsNet net)
    {
        if (net == null) return Component.translatable("message.beyond_integration.enchant_sep.network_not_exist");

        List<KeyAmount> books = findEnchantedBooks(net);

        if (books.isEmpty())
        {
            return Component.translatable("message.beyond_integration.enchant_sep.no_items");
        }

        int totalProcessed = 0;
        long totalXpCost = 0;
        long totalBooksNeeded = 0;

        for (KeyAmount entry : books)
        {
            if (!(entry.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.copyStackWithCount(1);
            // 受保护物品（beyond_integration:protect_sep 标记）：跳过手动批量分离
            if (isProtected(stack)) continue;
            List<EnchantmentInstance> ench = extractStoredEnchantments(stack);
            if (ench.size() <= 1) continue;

            long count = entry.amount();
            long cost = calcCost(ench, count);
            long booksNeeded = (ench.size() - 1) * count; // 原书本身也在网络中，只需补充 (N-1) 本

            if (!hasResources(net, cost, booksNeeded)) continue;
            if (!canStore(net, ench, count)) continue;

            consumeExperience(net, cost);
            consumeBooks(net, booksNeeded);

            // 先扣原多附魔书（检查成功，失败则退回已扣并跳过，防止刷书）
            KeyAmount removed = net.getUnifiedStorage().extract(ik, count, false, false);
            if (removed.amount() < count)
            {
                if (removed.amount() > 0) net.getUnifiedStorage().insert(ik, removed.amount(), false);
                continue;
            }

            for (EnchantmentInstance e : ench)
            {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                EnchantedBookItem.addEnchantment(book, e);
                net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
            }

            totalProcessed += count;
            totalXpCost += cost;
            totalBooksNeeded += booksNeeded;
        }

        Component report = Component.translatable("message.beyond_integration.enchant_sep.result", totalProcessed);
        if (totalXpCost > 0) report = report.copy().append(Component.translatable("message.beyond_integration.enchant_sep.xp_cost", totalXpCost / 20));
        if (totalBooksNeeded > 0) report = report.copy().append(Component.translatable("message.beyond_integration.enchant_sep.book_cost", totalBooksNeeded));
        return report;
    }

    /** 判断物品是否带附魔分离保护标记（beyond_integration:protect_sep） */
    private static boolean isProtected(ItemStack stack)
    {
        return stack.getTag() != null && stack.getTag().getBoolean("beyond_integration:protect_sep");
    }

    /** 将指定附魔列表逐条写入附魔书并输出到网络统一存储，每种附魔输出 count 本 */
    public static void doOutput(DimensionsNet net, List<EnchantmentInstance> ench, long count)
    {
        for (EnchantmentInstance e : ench)
        {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantedBookItem.addEnchantment(book, e);
            net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
        }
    }

    /** 扫描网络统一存储，收集所有多附魔附魔书（数量 > 0 的条目） */
    private static List<KeyAmount> findEnchantedBooks(DimensionsNet net)
    {
        List<KeyAmount> result = new ArrayList<>();
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return result;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++)
        {
            var raw = bucket.get(i);
            if (!(raw instanceof ItemStackKey ik)) continue;
            if (!(ik.getReadOnlyStack().getItem() instanceof EnchantedBookItem)) continue;
            long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
            if (amount > 0) result.add(new KeyAmount(ik, amount));
        }
        return result;
    }

    /** 从附魔书的 StoredEnchantments NBT 中解析全部已存储附魔 */
    public static List<EnchantmentInstance> extractStoredEnchantments(ItemStack book)
    {
        List<EnchantmentInstance> list = new ArrayList<>();
        CompoundTag tag = book.getTag();
        if (tag != null && tag.contains("StoredEnchantments", 9))
        {
            ListTag et = tag.getList("StoredEnchantments", 10);
            for (int i = 0; i < et.size(); i++)
            {
                CompoundTag t = et.getCompound(i);
                Enchantment e = BuiltInRegistries.ENCHANTMENT.get(ResourceLocation.tryParse(t.getString("id")));
                if (e != null) list.add(new EnchantmentInstance(e, t.getShort("lvl")));
            }
        }
        return list;
    }

    /**
     * 计算分离附魔所需的经验费用（返回 mB 流体值）。
     * 使用配置公式（useFormula）或"基础费用 + 等级差"两种模式，
     * 每种附魔单价乘以数量求和后 × 20（XP→mB）。
     */
    public static long calcCost(List<EnchantmentInstance> ench, long count)
    {
        long total = 0;
        for (EnchantmentInstance e : ench)
        {
            double mult = getMultiplier(e.enchantment);
            double perItemCost;
            if (CommandConfig.useFormula())
            {
                Map<String, Double> vars = new HashMap<>();
                vars.put("base", (double) CommandConfig.enchantmentSeparationBaseCost());
                vars.put("level", (double) e.level);
                vars.put("multiplier", mult);
                vars.put("books", (double) count);
                perItemCost = FormulaParser.evaluate(CommandConfig.costFormula(), vars);
            }
            else
            {
                int base = CommandConfig.enchantmentSeparationBaseCost();
                int lvlMult = CommandConfig.enchantmentSeparationLevelMultiplier();
                perItemCost = (base + (e.level - 1) * lvlMult) * mult;
            }
            total += (long) (perItemCost * count);
        }
        return total * 20; // 转 mb
    }

    /**
     * 获取指定附魔的费用倍率：优先匹配高费用附魔列表
     * （格式 modid:id:multiplier），未命中时返回默认倍率。
     */
    public static double getMultiplier(Enchantment e)
    {
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(e);
        if (id == null) return CommandConfig.defaultEnchantmentMultiplier();
        for (String entry : CommandConfig.highCostEnchantments())
        {
            String[] p = entry.split(":");
            if (p.length >= 2)
            {
                String eid = p[0] + ":" + p[1];
                if (eid.equals(id.toString()))
                {
                    if (p.length >= 3) { try { return Double.parseDouble(p[2]); } catch (Exception ignored) {} }
                    return CommandConfig.defaultEnchantmentMultiplier();
                }
            }
        }
        return CommandConfig.defaultEnchantmentMultiplier();
    }

    /** 检查网络是否拥有足够的经验流体与空白书用于分离 */
    public static boolean hasResources(DimensionsNet net, long xpCost, long booksNeeded)
    {
        if (xpCost > 0)
        {
            var xp = net.getUnifiedStorage().getStackByKey(xpFluidKey());
            if (xp.amount() < xpCost) return false;
        }
        if (booksNeeded > 0)
        {
            var bk = net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.BOOK)));
            if (bk.amount() < booksNeeded) return false;
        }
        return true;
    }

    /** 检查每种分离出的附魔书容量是否足以容纳 count 本 */
    public static boolean canStore(DimensionsNet net, List<EnchantmentInstance> ench, long count)
    {
        for (EnchantmentInstance e : ench)
        {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantedBookItem.addEnchantment(book, e);
            var cur = net.getUnifiedStorage().getStackByKey(new ItemStackKey(book));
            long cap = net.getUnifiedStorage().getSlotCapacity(0);
            if (cap <= 0) cap = Long.MAX_VALUE;
            if (cur.amount() + count > cap) return false;
        }
        return true;
    }

    /** 从网络扣除指定量的经验流体 */
    public static void consumeExperience(DimensionsNet net, long amount)
    {
        if (amount <= 0) return;
        net.getUnifiedStorage().extract(xpFluidKey(), amount, false, false);
    }

    /** 从网络扣除指定数量的空白书 */
    public static void consumeBooks(DimensionsNet net, long count)
    {
        if (count <= 0) return;
        net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.BOOK)), count, false, false);
    }

    /** 获取网络经验流体对应的存储键（优先 BD 的经验流体，否则空流体兜底） */
    public static FluidStackKey xpFluidKey()
    {
        if (BDFluids.XP_FLUID != null && BDFluids.XP_FLUID.source() != null)
        {
            return new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source().get(), 1));
        }
        return new FluidStackKey(new FluidStack(Fluids.EMPTY, 1));
    }
}
