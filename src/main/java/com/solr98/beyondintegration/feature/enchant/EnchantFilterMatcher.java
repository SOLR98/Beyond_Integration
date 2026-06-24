package com.solr98.beyondintegration.feature.enchant;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters items and enchantments according to the configured whitelist/blacklist.
 * <p>
 * Item filters support three match modes:
 * <ul>
 *   <li>Exact match: {@code "minecraft:diamond_sword"}</li>
 *   <li>Wildcard: {@code "minecraft:diamond_*"} matches all diamond items</li>
 *   <li>Tag: {@code "#minecraft:swords"} matches items in that tag</li>
 * </ul>
 */
public final class EnchantFilterMatcher {

    private EnchantFilterMatcher() {
        throw new AssertionError("No instances");
    }

    public static boolean passesItemFilter(ItemStack stack) {
        CommandConfig.EnchantItemFilterMode mode = CommandConfig.enchantItemFilterMode();
        if (mode == CommandConfig.EnchantItemFilterMode.DISABLED) return true;
        List<String> list = new ArrayList<>(CommandConfig.enchantItemFilterList());
        if (list.isEmpty()) return true;
        String id = stack.getItemHolder().getKey().location().toString();
        boolean matched = list.stream().anyMatch(entry -> matchItemFilter(entry, stack, id));
        return mode == CommandConfig.EnchantItemFilterMode.WHITELIST ? matched : !matched;
    }

    private static boolean matchItemFilter(String entry, ItemStack stack, String id) {
        if (entry.startsWith("#")) {
            var tag = ItemTags.create(ResourceLocation.parse(entry.substring(1)));
            return stack.is(tag);
        }
        if (entry.contains("*")) {
            String regex = "\\Q" + entry.replace("\\E", "\\E\\\\Q\\E").replace("*", "\\E.*\\Q") + "\\E";
            return id.matches(regex);
        }
        return id.equals(entry);
    }

    public static List<EnchantmentBookSeparatorHandler.EnchantEntry> filterEnchantments(
            List<EnchantmentBookSeparatorHandler.EnchantEntry> ench) {
        CommandConfig.EnchantFilterMode mode = CommandConfig.enchantFilterMode();
        if (mode == CommandConfig.EnchantFilterMode.DISABLED) return ench;
        List<String> list = new ArrayList<>(CommandConfig.enchantFilterList());
        if (list.isEmpty()) return ench;
        return ench.stream()
                .filter(e -> {
                    var key = e.holder().getKey();
                    if (key == null) return mode != CommandConfig.EnchantFilterMode.WHITELIST;
                    String id = key.location().toString();
                    return mode == CommandConfig.EnchantFilterMode.WHITELIST ? list.contains(id) : !list.contains(id);
                })
                .toList();
    }
}
