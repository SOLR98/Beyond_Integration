package com.solr98.beyondintegration.feature.feeder;

import cn.mlus.thirst.api.ThirstHelper;
import cn.mlus.thirst.content.purity.WaterPurity;
import cn.mlus.thirst.content.thirst.PlayerThirst;
import cn.mlus.thirst.foundation.common.capability.IThirst;
import cn.mlus.thirst.foundation.common.capability.ModCapabilities;
import cn.mlus.thirst.foundation.common.item.DrinkableItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Thirst was Reclaimed 后端实现（{@link ThirstBridge}）。
 * <p>仅在 {@code thirst} 加载时由 {@link ThirstBridge#get()} 选用。
 */
public final class ThirstCompat implements ThirstBridge
{
    public static final ThirstCompat INSTANCE = new ThirstCompat();

    private ThirstCompat() {}

    private static IThirst cap(Player player)
    {
        return player.getCapability(ModCapabilities.PLAYER_THIRST).orElse(null);
    }

    @Override
    public boolean loaded()
    {
        return true;
    }

    @Override
    public int primary(Player player)
    {
        IThirst c = cap(player);
        return c == null ? 20 : c.getThirst();
    }

    @Override
    public int secondary(Player player)
    {
        IThirst c = cap(player);
        return c == null ? 0 : c.getQuenched();
    }

    @Override
    public void add(Player player, int primary, int secondary, int purity)
    {
        IThirst c = cap(player);
        if (c == null) return;
        if (purity >= 0) WaterPurity.givePurityEffects(player, purity);
        c.drink(player, Math.max(1, primary), secondary);
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
        return WaterPurity.hasPurity(stack) ? WaterPurity.getPurity(stack) : -1;
    }
}
