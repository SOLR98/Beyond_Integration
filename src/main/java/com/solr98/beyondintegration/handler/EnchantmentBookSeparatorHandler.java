package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class EnchantmentBookSeparatorHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record Entry(Holder<Enchantment> holder, int level) {}

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!(net instanceof EnchantSeparationAccessor ea) || !ea.beyond$isEnchantSeparationEnabled())
            return pass(tryInsert);
        return handleSeparation(tryInsert, net);
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo handleSeparation(
            KeyAmount tryInsert, DimensionsNet net) {
        if (!CommandConfig.SERVER.enchantSeparation.get())
            return pass(tryInsert);
        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey))
            return pass(tryInsert);

        ItemStack stack = itemStackKey.copyStackWithCount(1);
        if (stack.isEmpty() || net == null)
            return pass(tryInsert);

        // Enchanted book with multiple stored enchantments
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && stored.size() > 1) {
            List<Entry> ench = new ArrayList<>();
            for (Holder<Enchantment> holder : stored.keySet()) {
                int level = stored.getLevel(holder);
                if (level > 0) ench.add(new Entry(holder, level));
            }
            if (ench.size() <= 1) return pass(tryInsert);
            return separateBook(tryInsert, net, ench);
        }

        // Enchanted item (tools, weapons, armor)
        if (stack.isEnchanted()) {
            if (!CommandConfig.SERVER.enchantItemSeparation.get())
                return pass(tryInsert);
            return separateItem(tryInsert, net, stack);
        }

        return pass(tryInsert);
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateBook(
            KeyAmount tryInsert, DimensionsNet net, List<Entry> ench) {
        long count = tryInsert.amount();
        long cost = calcCost(ench, count);
        long booksNeeded = (ench.size() - 1) * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, ench, count))
            return pass(tryInsert);

        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, ench, count);
        return acceptEmpty();
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateItem(
            KeyAmount tryInsert, DimensionsNet net, ItemStack itemStack) {
        ItemEnchantments ench = itemStack.get(DataComponents.ENCHANTMENTS);
        if (ench == null || ench.isEmpty()) return pass(tryInsert);

        List<Entry> enchList = new ArrayList<>();
        for (Holder<Enchantment> holder : ench.keySet()) {
            int level = ench.getLevel(holder);
            if (level > 0) enchList.add(new Entry(holder, level));
        }

        long count = tryInsert.amount();
        long cost = (long) (calcCost(enchList, count) * CommandConfig.SERVER.enchantItemMult.get());
        long booksNeeded = enchList.size() * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, enchList, count))
            return pass(tryInsert);

        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, enchList, count);

        ItemStack base = itemStack.copy();
        base.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        net.getUnifiedStorage().insert(new ItemStackKey(base), count, false);

        return acceptEmpty();
    }

    private static void doOutput(DimensionsNet net, List<Entry> ench, long count) {
        for (Entry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder, e.level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
        }
    }

    // ═══════════════════════════════════════════
    //  Manual bulk separation
    // ═══════════════════════════════════════════

    public static Component separateAll(DimensionsNet net) {
        if (net == null)
            return Component.translatable("message.beyond_integration.enchant_sep.network_not_exist");

        List<KeyAmount> books = findEnchantedBooks(net);
        List<EnchantedItem> items = findEnchantedItems(net);
        if (books.isEmpty() && items.isEmpty())
            return Component.translatable("message.beyond_integration.enchant_sep.no_items");

        int totalProcessed = 0;
        long totalXpCost = 0;
        long totalBooksNeeded = 0;

        for (KeyAmount entry : books) {
            if (!(entry.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.copyStackWithCount(1);
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored == null || stored.size() <= 1) continue;
            List<Entry> ench = new ArrayList<>();
            for (Holder<Enchantment> holder : stored.keySet()) {
                int level = stored.getLevel(holder);
                if (level > 0) ench.add(new Entry(holder, level));
            }
            if (ench.size() <= 1) continue;

            long count = entry.amount();
            long cost = calcCost(ench, count);
            long booksNeeded = (ench.size() - 1) * count;
            if (!hasResources(net, cost, booksNeeded)) continue;
            if (!canStore(net, ench, count)) continue;

            consumeExperience(net, cost);
            consumeBooks(net, booksNeeded);
            doOutput(net, ench, count);
            totalProcessed += count;
            totalXpCost += cost;
            totalBooksNeeded += booksNeeded;
        }

        for (EnchantedItem ei : items) {
            ItemEnchantments ench = ei.stack.get(DataComponents.ENCHANTMENTS);
            if (ench == null || ench.isEmpty()) continue;
            List<Entry> enchList = new ArrayList<>();
            for (Holder<Enchantment> holder : ench.keySet()) {
                int level = ench.getLevel(holder);
                if (level > 0) enchList.add(new Entry(holder, level));
            }
            long count = ei.amount;
            long cost = (long) (calcCost(enchList, count) * CommandConfig.SERVER.enchantItemMult.get());
            long booksNeeded = enchList.size() * count;
            if (!hasResources(net, cost, booksNeeded)) continue;
            if (!canStore(net, enchList, count)) continue;

            consumeExperience(net, cost);
            consumeBooks(net, booksNeeded);
            doOutput(net, enchList, count);
            net.getUnifiedStorage().extract(ei.key, count, false, false);

            ItemStack base = ei.stack.copy();
            base.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
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

    // ═══════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════

    private record EnchantedItem(ItemStackKey key, ItemStack stack, long amount) {}

    private static List<KeyAmount> findEnchantedBooks(DimensionsNet net) {
        List<KeyAmount> result = new ArrayList<>();
        var opt = net.getUnifiedStorage().getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return result;
        var bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            var raw = bucket.get(i);
            if (!(raw instanceof ItemStackKey ik)) continue;
            ItemStack s = ik.getReadOnlyStack();
            if (!s.is(Items.ENCHANTED_BOOK)) continue;
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
            ItemStack s = ik.getReadOnlyStack();
            if (s.is(Items.ENCHANTED_BOOK)) continue;
            if (!s.isEnchanted()) continue;
            long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
            if (amount > 0) result.add(new EnchantedItem(ik, s, amount));
        }
        return result;
    }

    private static long calcCost(List<Entry> ench, long count) {
        long total = 0;
        int base = CommandConfig.SERVER.enchantBaseCost.get();
        double lvlMult = CommandConfig.SERVER.enchantLevelMult.get();
        for (Entry e : ench) {
            long xp = (long) ((base + (e.level - 1) * lvlMult) * count);
            double mult = getMultiplier(e.holder);
            total += (long) (xp * mult);
        }
        return total * 20;
    }

    private static double getMultiplier(Holder<Enchantment> holder) {
        var key = holder.getKey();
        if (key == null) return CommandConfig.SERVER.enchantDefaultMult.get();
        ResourceLocation id = key.location();
        for (String entry : CommandConfig.SERVER.enchantHighCostList.get()) {
            String[] p = entry.split(":");
            if (p.length >= 2) {
                String eid = p[0] + ":" + p[1];
                if (eid.equals(id.toString())) {
                    if (p.length >= 3) {
                        try { return Double.parseDouble(p[2]); } catch (Exception ignored) {}
                    }
                    return CommandConfig.SERVER.enchantDefaultMult.get();
                }
            }
        }
        return CommandConfig.SERVER.enchantDefaultMult.get();
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

    private static boolean canStore(DimensionsNet net, List<Entry> ench, long count) {
        for (Entry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder, e.level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
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

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo pass(KeyAmount input) {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(input, false);
    }

    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo acceptEmpty() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(new ItemStack(Items.AIR)), 0), false);
    }
}
