package com.solr98.beyondintegration.feature.enchant;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automatically separates enchantments from items/books inserted into a BD network.
 * <p>
 * When enabled, this handler intercepts every item insertion and:
 * <ol>
 *   <li>Detects enchanted books with multiple enchantments → splits into individual books</li>
 *   <li>Detects enchanted items (tools, weapons, armor) → strips enchantments into books</li>
 * </ol>
 * Costs are paid from the network's XP fluid storage (BDFluids.XP_FLUID).
 * Consumes regular books from network storage to serve as containers.
 * <p>
 * Also provides {@link #separateAll(DimensionsNet)} for manual bulk separation via command.
 */
public class EnchantmentBookSeparatorHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public record EnchantEntry(Holder<Enchantment> holder, int level) {}

    private record EnchantedItem(ItemStackKey key, ItemStack stack, long amount) {}

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!(net instanceof EnchantSeparationAccessor ea) || !ea.beyond$isEnchantSeparationEnabled())
            return pass(tryInsert);
        return handleSeparation(tryInsert, net);
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo handleSeparation(
            KeyAmount tryInsert, DimensionsNet net) {
        if (!CommandConfig.enableEnchantSeparation())
            return pass(tryInsert);
        if (!(tryInsert.key() instanceof ItemStackKey itemStackKey))
            return pass(tryInsert);

        ItemStack stack = itemStackKey.copyStackWithCount(1);
        if (stack.isEmpty() || net == null)
            return pass(tryInsert);
        if (!EnchantFilterMatcher.passesItemFilter(stack))
            return pass(tryInsert);
        if (ItemTooltipHandler.isProtected(stack))
            return pass(tryInsert);

        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && stored.size() > 1) {
            List<EnchantEntry> ench = extractEnchants(stored);
            ench = EnchantFilterMatcher.filterEnchantments(ench);
            if (ench.size() <= 1) return pass(tryInsert);
            return separateBook(tryInsert, net, ench);
        }

        if (stack.isEnchanted()) {
            if (!CommandConfig.enableItemEnchantSeparation())
                return pass(tryInsert);
            ItemEnchantments itemEnch = stack.get(DataComponents.ENCHANTMENTS);
            if (itemEnch == null || itemEnch.isEmpty()) return pass(tryInsert);
            List<EnchantEntry> ench = extractEnchants(itemEnch);
            ench = EnchantFilterMatcher.filterEnchantments(ench);
            if (ench.isEmpty()) return pass(tryInsert);
            return separateItem(tryInsert, net, stack, itemEnch, ench);
        }

        return pass(tryInsert);
    }

    private static List<EnchantEntry> extractEnchants(ItemEnchantments enchData) {
        List<EnchantEntry> list = new ArrayList<>();
        for (Holder<Enchantment> holder : enchData.keySet()) {
            int level = enchData.getLevel(holder);
            if (level > 0) list.add(new EnchantEntry(holder, level));
        }
        return list;
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateBook(
            KeyAmount tryInsert, DimensionsNet net, List<EnchantEntry> ench) {
        long count = tryInsert.amount();
        long cost = EnchantCostCalculator.calcCost(ench, count);
        long booksNeeded = (ench.size() - 1) * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, ench, count))
            return pass(tryInsert);

        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, ench, count);
        return acceptEmpty();
    }

    private UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo separateItem(
            KeyAmount tryInsert, DimensionsNet net, ItemStack itemStack,
            ItemEnchantments allEnch, List<EnchantEntry> toSeparate) {
        long count = tryInsert.amount();
        long cost = (long) (EnchantCostCalculator.calcCost(toSeparate, count) * CommandConfig.itemSeparationMultiplier());
        long booksNeeded = toSeparate.size() * count;

        if (!hasResources(net, cost, booksNeeded) || !canStore(net, toSeparate, count))
            return pass(tryInsert);

        consumeExperience(net, cost);
        consumeBooks(net, booksNeeded);
        doOutput(net, toSeparate, count);

        Set<Holder<Enchantment>> separated = toSeparate.stream().map(EnchantEntry::holder).collect(Collectors.toSet());
        ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : allEnch.keySet()) {
            int level = allEnch.getLevel(holder);
            if (level > 0 && !separated.contains(holder))
                remaining.set(holder, level);
        }
        ItemStack base = itemStack.copy();
        base.set(DataComponents.ENCHANTMENTS, remaining.toImmutable());
        net.getUnifiedStorage().insert(new ItemStackKey(base), count, false);

        return acceptEmpty();
    }

    static void doOutput(DimensionsNet net, List<EnchantEntry> ench, long count) {
        for (EnchantEntry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder(), e.level());
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            net.getUnifiedStorage().insert(new ItemStackKey(book), count, false);
        }
    }

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
            List<EnchantEntry> ench = extractEnchants(stored);
            if (ench.size() <= 1) continue;

            long count = entry.amount();
            long cost = EnchantCostCalculator.calcCost(ench, count);
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
            ItemEnchantments enchData = ei.stack.get(DataComponents.ENCHANTMENTS);
            if (enchData == null || enchData.isEmpty()) continue;
            List<EnchantEntry> enchList = extractEnchants(enchData);
            long count = ei.amount;
            long cost = (long) (EnchantCostCalculator.calcCost(enchList, count) * CommandConfig.itemSeparationMultiplier());
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

    private static boolean canStore(DimensionsNet net, List<EnchantEntry> ench, long count) {
        for (EnchantEntry e : ench) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(e.holder(), e.level());
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            var cur = net.getUnifiedStorage().getStackByKey(new ItemStackKey(book));
            long cap = Long.MAX_VALUE;
            try {
                long slotCap = net.getUnifiedStorage().getSlotCapacity(0);
                if (slotCap > 0) cap = slotCap;
            } catch (Exception ex) {
                LOGGER.warn("[BD-Integration] Failed to get slot capacity from network '{}': {}",
                        net.getId(), ex.getMessage());
            }
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
