package com.solr98.beyondintegration.feature.enchant;

import com.solr98.beyondintegration.CommandConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EnchantCostCalculator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EnchantCostCalculator() {
        throw new AssertionError("No instances");
    }

    public static long calcCost(List<EnchantmentBookSeparatorHandler.EnchantEntry> ench, long count) {
        long total = 0;
        for (EnchantmentBookSeparatorHandler.EnchantEntry e : ench) {
            double mult = getMultiplier(e.holder());
            double perItemCost;
            if (CommandConfig.useFormula()) {
                Map<String, Double> vars = new HashMap<>();
                vars.put("base", (double) CommandConfig.enchantBaseCost());
                vars.put("level", (double) e.level());
                vars.put("multiplier", mult);
                vars.put("books", (double) count);
                perItemCost = FormulaParser.evaluate(CommandConfig.costFormula(), vars);
            } else {
                int base = CommandConfig.enchantBaseCost();
                double lvlMult = CommandConfig.enchantLevelMult();
                perItemCost = (base + (e.level() - 1) * lvlMult) * mult;
            }
            total += (long) (perItemCost * count);
        }
        return total * 20;
    }

    static double getMultiplier(Holder<Enchantment> holder) {
        var key = holder.getKey();
        if (key == null) return CommandConfig.enchantDefaultMult();
        ResourceLocation id = key.location();
        for (String entry : CommandConfig.enchantHighCostList()) {
            int lastColon = entry.lastIndexOf(':');
            if (lastColon <= 0) continue;
            String enchantId = entry.substring(0, lastColon);
            if (!enchantId.equals(id.toString())) continue;
            String multStr = entry.substring(lastColon + 1);
            try { return Double.parseDouble(multStr); } catch (NumberFormatException ex) {
                LOGGER.warn("[BD-Integration] Invalid high_cost multiplier in config: '{}'", entry);
            }
            return CommandConfig.enchantDefaultMult();
        }
        return CommandConfig.enchantDefaultMult();
    }
}
