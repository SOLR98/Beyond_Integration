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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnchantmentBookSeparatorHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            DimensionsNet net) {
        // 检查该网络是否启用了附魔分离（网络级别开关）
        if (!(net instanceof com.solr98.beyondintegration.handler.EnchantSeparationAccessor ea)
                || !ea.beyond$isEnchantSeparationEnabled()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        return handleSeparation(tryInsert, net);
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo handleSeparation(
            KeyAmount tryInsert, DimensionsNet net) {
        if (!CommandConfig.enableEnchantmentSeparation()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack itemStack = itemStackKey.copyStackWithCount(1);
        if (itemStack.isEmpty()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        if (net == null) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // 附魔书：仅分离多附魔书
        if (itemStack.getItem() instanceof EnchantedBookItem) {
            List<EnchantmentInstance> ench = extractStoredEnchantments(itemStack);
            if (ench.size() <= 1) {
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
            }
            return separateBook(tryInsert, net, ench);
        }

        // 附魔工具/武器/盔甲
        if (itemStack.isEnchanted()) {
            if (!CommandConfig.enableItemEnchantmentSeparation()) {
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
            }
            return separateItem(tryInsert, net, itemStack);
        }

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
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

    // ── 分离单件附魔物品 ──
    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateItem(
            KeyAmount tryInsert, DimensionsNet net, ItemStack itemStack) {
        Map<Enchantment, Integer> enchMap = EnchantmentHelper.getEnchantments(itemStack);
        if (enchMap.isEmpty()) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        long count = tryInsert.amount();
        List<EnchantmentInstance> enchList = new ArrayList<>();
        enchMap.forEach((e, lvl) -> enchList.add(new EnchantmentInstance(e, lvl)));

        long cost = (long)(calcCost(enchList, count) * CommandConfig.itemSeparationMultiplier());
        long booksNeeded = enchList.size() * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, enchList, count)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, enchList, count);

        // 返还去附魔后的基础物品
        ItemStack base = itemStack.copy();
        EnchantmentHelper.setEnchantments(new HashMap<>(), base);
        net.getUnifiedStorage().insert(new ItemStackKey(base), count, false);

        return acceptEmpty();
    }

    private static void doOutput(DimensionsNet net, List<EnchantmentInstance> ench, long count) {
        for (EnchantmentInstance e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantedBookItem.addEnchantment(book, e);
            net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
        }
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo acceptEmpty() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(new ItemStack(Items.AIR)), 0), false);
    }

    // ═══════════════════════════════════════════════════════════════
    // 以下为可被命令调用的手动分离逻辑
    // ═══════════════════════════════════════════════════════════════

    /** 对网络内所有附魔书和附魔物品进行分离，返回处理摘要 */
    public static Component separateAll(DimensionsNet net) {
        if (net == null) return Component.translatable("message.beyond_integration.enchant_sep.network_not_exist");

        List<KeyAmount> books = findEnchantedBooks(net);
        List<EnchantedItem> items = findEnchantedItems(net);
        int bookTypes = books.size();
        int itemTypes = items.size();

        if (bookTypes == 0 && itemTypes == 0) {
            return Component.translatable("message.beyond_integration.enchant_sep.no_items");
        }

        int totalProcessed = 0;
        long totalXpCost = 0;
        long totalBooksNeeded = 0;

        // 处理附魔书
        for (KeyAmount entry : books) {
            if (!(entry.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.copyStackWithCount(1);
            List<EnchantmentInstance> ench = extractStoredEnchantments(stack);
            if (ench.size() <= 1) continue;

            long count = entry.amount();
            long cost = calcCost(ench, count);
            long booksNeeded = (ench.size() - 1) * count; // 原书本身也在网络中，只需补充 (N-1) 本

            if (!hasResources(net, cost, booksNeeded)) continue;
            if (!canStore(net, ench, count)) continue;

            consumeExperience(net, cost);
            consumeBooks(net, booksNeeded);

            for (EnchantmentInstance e : ench) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                EnchantedBookItem.addEnchantment(book, e);
                net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
            }

            totalProcessed += count;
            totalXpCost += cost;
            totalBooksNeeded += booksNeeded;
        }

        // 处理附魔物品
        for (EnchantedItem ei : items) {
            long count = ei.amount;
            Map<Enchantment, Integer> enchMap = EnchantmentHelper.getEnchantments(ei.stack);
            if (enchMap.isEmpty()) continue;

            List<EnchantmentInstance> enchList = new ArrayList<>();
            enchMap.forEach((e, lvl) -> enchList.add(new EnchantmentInstance(e, lvl)));

            long cost = (long)(calcCost(enchList, count) * CommandConfig.itemSeparationMultiplier());
            long booksNeeded = enchList.size() * count;

            if (!hasResources(net, cost, booksNeeded)) continue;
            if (!canStore(net, enchList, count)) continue;

            consumeExperience(net, cost);
            consumeBooks(net, booksNeeded);

            // 输出单一附魔书
            for (EnchantmentInstance e : enchList) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                EnchantedBookItem.addEnchantment(book, e);
                net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
            }

            // 消耗原物品
            net.getUnifiedStorage().extract(ei.key, count, false, false);

            // 返还基础物品（无附魔）
            ItemStack base = ei.stack.copy();
            EnchantmentHelper.setEnchantments(new HashMap<>(), base);
            net.getUnifiedStorage().insert(new ItemStackKey(base), count, false);

            totalProcessed += count;
            totalXpCost += cost;
            totalBooksNeeded += booksNeeded;
        }

        Component report = Component.translatable("message.beyond_integration.enchant_sep.result", totalProcessed);
        if (totalXpCost > 0) report = report.copy().append(Component.translatable("message.beyond_integration.enchant_sep.xp_cost", totalXpCost / 20));
        if (totalBooksNeeded > 0) report = report.copy().append(Component.translatable("message.beyond_integration.enchant_sep.book_cost", totalBooksNeeded));
        return report;
    }

    private record EnchantedItem(ItemStackKey key, ItemStack stack, long amount) {}

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

    private static List<EnchantedItem> findEnchantedItems(DimensionsNet net) {
        List<EnchantedItem> result = new ArrayList<>();
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return result;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            var raw = bucket.get(i);
            if (!(raw instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            if (stack.getItem() instanceof EnchantedBookItem) continue;
            if (!stack.isEnchanted()) continue;
            long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
            if (amount > 0) result.add(new EnchantedItem(ik, stack, amount));
        }
        return result;
    }

    private static List<EnchantmentInstance> extractStoredEnchantments(ItemStack book) {
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

    private static long calcCost(List<EnchantmentInstance> ench, long count) {
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

    private static double getMultiplier(Enchantment e) {
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

    private static boolean hasResources(DimensionsNet net, long xpCost, long booksNeeded) {
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

    private static boolean canStore(DimensionsNet net, List<EnchantmentInstance> ench, long count) {
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

    private static void consumeExperience(DimensionsNet net, long amount) {
        if (amount <= 0) return;
        net.getUnifiedStorage().extract(xpFluidKey(), amount, false, false);
    }

    private static void consumeBooks(DimensionsNet net, long count) {
        if (count <= 0) return;
        net.getUnifiedStorage().extract(new ItemStackKey(new ItemStack(Items.BOOK)), count, false, false);
    }

    private static FluidStackKey xpFluidKey() {
        if (BDFluids.XP_FLUID != null && BDFluids.XP_FLUID.source() != null) {
            return new FluidStackKey(new FluidStack(BDFluids.XP_FLUID.source().get(), 1));
        }
        return new FluidStackKey(new FluidStack(Fluids.EMPTY, 1));
    }
}
