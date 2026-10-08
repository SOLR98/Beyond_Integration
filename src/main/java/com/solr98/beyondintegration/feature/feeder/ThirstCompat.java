package com.solr98.beyondintegration.feature.feeder;

import cn.mlus.thirst.api.ThirstHelper;
import cn.mlus.thirst.content.purity.WaterPurity;
import cn.mlus.thirst.content.thirst.PlayerThirst;
import cn.mlus.thirst.foundation.common.capability.ModAttachment;
import cn.mlus.thirst.foundation.common.item.DrinkableItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Thirst was Reclaimed 后端实现（{@link ThirstBridge}，NeoForge 1.21.1）。
 * <p>1.21.1 用 DataAttachment（{@link ModAttachment#PLAYER_THIRST}）承载玩家口渴数据。
 * 仅在 {@code thirst} 加载时由 {@link ThirstBridge#get()} 选用。
 */
public final class ThirstCompat implements ThirstBridge
{
    public static final ThirstCompat INSTANCE = new ThirstCompat();

    private ThirstCompat() {}

    private static PlayerThirst cap(Player player)
    {
        return player.getData(ModAttachment.PLAYER_THIRST.get());
    }

    @Override
    public boolean loaded()
    {
        return true;
    }

    @Override
    public int primary(Player player)
    {
        PlayerThirst c = cap(player);
        return c == null ? 20 : c.getThirst();
    }

    @Override
    public int secondary(Player player)
    {
        PlayerThirst c = cap(player);
        return c == null ? 0 : c.getQuenched();
    }

    @Override
    public void add(Player player, int primary, int secondary, int purity)
    {
        PlayerThirst c = cap(player);
        if (c == null) return;
        if (purity >= 0) WaterPurity.givePurityEffects(player, purity);
        c.drink(Math.max(1, primary), secondary);
    }

    @Override
    public boolean hydratesViaEat()
    {
        return true;
    }

    @Override
    public boolean restoresThirst(ItemStack stack)
    {
        return ThirstHelper.itemRestoresThirst(stack);
    }

    @Override
    public void drinkItem(ItemStack stack, Player player)
    {
        PlayerThirst.drink(stack, player);
    }

    @Override
    public boolean drinksOnFinish(Item item)
    {
        return item instanceof DrinkableItem;
    }

    @Override
    public void tagPurity(ItemStack stack, int purity)
    {
        if (stack == null || stack.isEmpty()) return;
        WaterPurity.addPurity(stack, purity);
    }

    @Override
    public int readPurity(ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return -1;
        Integer p = WaterPurity.hasPurity(stack) ? WaterPurity.getPurity(stack) : null;
        return p == null ? -1 : p;
    }
}
