package com.solr98.beyondintegration.feature.enchant;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;


import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔分离处理器：维度网络"插入前"拦截器。
 * 多附魔书 / 附魔工具、武器、盔甲进入网络时，按配置将附魔逐条分离为
 * 单一附魔书（附魔书）或"单一附魔书 + 去附魔基础物品"（附魔物品），
 * 并从网络中扣除经验（XP 流体）与空白书作为消耗。
 * 支持自定义费用公式（FormulaParser）与网络级开关（EnchantSeparationAccessor）。
 */
public class EnchantmentBookSeparatorHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {
        // 调试日志：入口信息（网络/物品/开关）
        if (CommandConfig.enchantmentSeparationDebug()) {
            LOGGER.info("[enchant-sep] beforeInsert net={} item={} amount={} netSwitch={}",
                    net != null ? net.getId() : -1,
                    tryInsert.key() instanceof ItemStackKey ik ? ik.getReadOnlyStack().getHoverName().getString() : tryInsert.key().getClass().getSimpleName(),
                    tryInsert.amount(),
                    net instanceof com.solr98.beyondintegration.handler.EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled());
        }
        // 检查该网络是否启用了附魔分离（网络级别开关）
        if (!(net instanceof com.solr98.beyondintegration.handler.EnchantSeparationAccessor ea)
                || !ea.beyond$isEnchantSeparationEnabled()) {
            if (CommandConfig.enchantmentSeparationDebug())
                LOGGER.info("[enchant-sep] pass: net switch off or not accessor");
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        return handleSeparation(tryInsert, net);
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo handleSeparation(
            KeyAmount tryInsert, DimensionsNet net) {
        if (!CommandConfig.enableEnchantmentSeparation()) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: global enchantSeparation off");
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: non-item key {}", tryInsert.key().getClass().getSimpleName());
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack itemStack = itemStackKey.getReadOnlyStack().copy();
        if (itemStack.isEmpty()) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: empty stack");
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (net == null) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: null net");
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // 受保护物品（beyond_integration:protect_sep 标记，GUI 右键切换）：跳过自动分离
        if (itemStack.getTag() != null && itemStack.getTag().getBoolean("beyond_integration:protect_sep")) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: protected item");
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // 仅处理多附魔书（附魔物品/神化装备分离已存档移除，见 archived_features/enchant_item_apoth_separation）
        if (!(itemStack.getItem() instanceof EnchantedBookItem)) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: not an enchanted book");
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        List<EnchantmentInstance> ench = extractStoredEnchantments(itemStack);
        if (ench.size() <= 1) {
            if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] pass: book has {} effective enchants", ench.size());
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        if (CommandConfig.enchantmentSeparationDebug()) LOGGER.info("[enchant-sep] separate book {} ({} enchants)", itemStack.getHoverName().getString(), ench.size());
        return separateBook(tryInsert, net, ench);
    }

    // ── 分离单本多附魔书 ──
    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateBook(
            KeyAmount tryInsert, DimensionsNet net, List<EnchantmentInstance> ench) {
        long count = tryInsert.amount();
        long cost = calcCost(ench, count);
        long booksNeeded = (ench.size() - 1) * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, ench, count)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, ench, count);
        return acceptEmpty();
    }

    /** 返回"已消费"空结果，即拦截原插入（数量为 0 的空气键） */
    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo acceptEmpty() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(new ItemStack(Items.AIR)), 0), false);
    }

    // 以下为可被命令调用的手动分离逻辑
    // ═══════════════════════════════════════════════════════════════

    /** 对网络内所有多附魔书进行分离，返回处理摘要 */
    public static Component separateAll(DimensionsNet net) {
        if (net == null) return Component.translatable("message.beyond_integration.enchant_sep.network_not_exist");

        List<KeyAmount> books = findEnchantedBooks(net);

        if (books.isEmpty()) {
            return Component.translatable("message.beyond_integration.enchant_sep.no_items");
        }

        int totalProcessed = 0;
        long totalXpCost = 0;
        long totalBooksNeeded = 0;

        // 处理附魔书
        for (KeyAmount entry : books) {
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
            if (removed.amount() < count) {
                if (removed.amount() > 0) net.getUnifiedStorage().insert(ik, removed.amount(), false);
                continue;
            }

            for (EnchantmentInstance e : ench) {
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

    /** 判断物品是否带附魔分离保护标记（beyond_integration:protect_sep，GUI Ctrl+右键切换） */
    private static boolean isProtected(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean("beyond_integration:protect_sep");
    }

    /** 将指定附魔列表逐条写入附魔书并输出到网络统一存储，每种附魔输出 count 本 */
    public static void doOutput(DimensionsNet net, List<EnchantmentInstance> ench, long count) {
        for (EnchantmentInstance e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantedBookItem.addEnchantment(book, e);
            net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
        }
    }

    /** 记录待分离附魔物品的信息：存储键、物品栈、数量 */

    /** 扫描网络统一存储，收集所有多附魔附魔书（数量 > 0 的条目） */
    private static List<KeyAmount> findEnchantedBooks(DimensionsNet net) {
        List<KeyAmount> result = new ArrayList<>();
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return result;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            var raw = bucket.get(i);
            if (!(raw instanceof ItemStackKey ik)) continue;
            if (!(ik.getReadOnlyStack().getItem() instanceof EnchantedBookItem)) continue;
            long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
            if (amount > 0) result.add(new KeyAmount(ik, amount));
        }
        return result;
    }

    /** 扫描网络统一存储，收集所有带附魔的非附魔书物品（工具/武器/盔甲） */

    /** 从附魔书的 StoredEnchantments NBT 中解析全部已存储附魔 */
    public static List<EnchantmentInstance> extractStoredEnchantments(ItemStack book) {
        List<EnchantmentInstance> list = new ArrayList<>();
        CompoundTag tag = book.getTag();
        if (tag != null && tag.contains("StoredEnchantments", 9)) {
            ListTag et = tag.getList("StoredEnchantments", 10);
            for (int i = 0; i < et.size(); i++) {
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
    public static long calcCost(List<EnchantmentInstance> ench, long count) {
        long total = 0;
        for (EnchantmentInstance e : ench) {
            double mult = getMultiplier(e.enchantment);
            double perItemCost;
            if (CommandConfig.useFormula()) {
                Map<String, Double> vars = new HashMap<>();
                vars.put("base", (double) CommandConfig.enchantmentSeparationBaseCost());
                vars.put("level", (double) e.level);
                vars.put("multiplier", mult);
                vars.put("books", (double) count);
                perItemCost = FormulaParser.evaluate(CommandConfig.costFormula(), vars);
            } else {
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
    public static double getMultiplier(Enchantment e) {
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(e);
        if (id == null) return CommandConfig.defaultEnchantmentMultiplier();
        for (String entry : CommandConfig.highCostEnchantments()) {
            String[] p = entry.split(":");
            if (p.length >= 2) {
                String eid = p[0] + ":" + p[1];
                if (eid.equals(id.toString())) {
                    if (p.length >= 3) { try { return Double.parseDouble(p[2]); } catch (Exception ignored) {} }
                    return CommandConfig.defaultEnchantmentMultiplier();
                }
            }
        }
        return CommandConfig.defaultEnchantmentMultiplier();
    }

    /** 检查网络是否拥有足够的经验流体与空白书用于分离 */
    public static boolean hasResources(DimensionsNet net, long xpCost, long booksNeeded) {
        if (xpCost > 0) {
            var xp = net.getUnifiedStorage().getStackByKey(xpFluidKey());
            if (xp.amount() < xpCost) return false;
        }
        if (booksNeeded > 0) {
            var bk = net.getUnifiedStorage().getStackByKey(new ItemStackKey(new ItemStack(Items.BOOK)));
            if (bk.amount() < booksNeeded) return false;
        }
        return true;
    }

    /** 检查每种分离出的附魔书容量是否足以容纳 count 本 */
    public static boolean canStore(DimensionsNet net, List<EnchantmentInstance> ench, long count) {
        for (EnchantmentInstance e : ench) {
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
    public static void consumeExperience(DimensionsNet net, long amount) {
        if (amount <= 0) return;
        net.getUnifiedStorage().extract(xpFluidKey(), amount, false, false);
    }

    /** 从网络扣除指定数量的空白书 */
    public static void consumeBooks(DimensionsNet net, long count) {
        if (count <= 0) return;
        net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.BOOK)), count, false, false);
    }

    /** 获取网络经验流体对应的存储键（优先 BD 的经验流体，否则空流体兜底） */
    public static FluidStackKey xpFluidKey() {
        if (BDFluids.XP_FLUID != null && BDFluids.XP_FLUID.source() != null) {
            return new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source().get(), 1));
        }
        return new FluidStackKey(new FluidStack(Fluids.EMPTY, 1));
    }
}
