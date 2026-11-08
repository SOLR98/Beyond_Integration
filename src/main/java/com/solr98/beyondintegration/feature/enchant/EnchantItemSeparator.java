package com.solr98.beyondintegration.feature.enchant;

import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从装备槽的附魔物品分出附魔：消耗网络 XP + 空白书，产出单附魔书入网络，
 * 装备去掉附魔后保留本体（仍留在装备槽）。
 */
public final class EnchantItemSeparator {

    private EnchantItemSeparator() {
    }

    /**
     * 对单个附魔物品执行附魔分离。
     *
     * @return 是否成功分离（资源不足 / 无附魔 / 无网络 → {@code false}）
     */
    public static boolean separate(ItemStack item, DimensionsNet net) {
        if (!com.solr98.beyondintegration.CommandConfig.enchantSeparationEnabled()) return false;
        if (item == null || item.isEmpty() || net == null) return false;
        var map = EnchantmentHelper.getEnchantmentsForCrafting(item);
        if (map.isEmpty()) return false;

        List<EnchantmentBookSeparatorHandler.Entry> list = new ArrayList<>();
        for (var holder : map.keySet()) {
            list.add(new EnchantmentBookSeparatorHandler.Entry(holder, map.getLevel(holder)));
        }

        long cost = EnchantmentBookSeparatorHandler.calcCost(list, 1L);
        long books = list.size();
        if (!EnchantmentBookSeparatorHandler.hasResources(net, cost, books)) return false;
        if (!EnchantmentBookSeparatorHandler.canStore(net, list, 1L)) return false;

        EnchantmentBookSeparatorHandler.consumeExperience(net, cost);
        EnchantmentBookSeparatorHandler.consumeBooks(net, books);
        EnchantmentBookSeparatorHandler.doOutput(net, list, 1L);

        // 装备去掉附魔，保留其余组件 / 状态
        EnchantmentHelper.setEnchantments(item, ItemEnchantments.EMPTY);
        return true;
    }
}
