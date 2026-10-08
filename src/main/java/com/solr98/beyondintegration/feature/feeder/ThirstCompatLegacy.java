package com.solr98.beyondintegration.feature.feeder;

import dev.ghen.thirst.api.ThirstHelper;
import dev.ghen.thirst.content.purity.WaterPurity;
import dev.ghen.thirst.content.thirst.PlayerThirst;
import dev.ghen.thirst.foundation.common.capability.IThirst;
import dev.ghen.thirst.foundation.common.capability.ModCapabilities;
import dev.ghen.thirst.foundation.common.item.DrinkableItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 旧版「Thirst was Taken」（{@code dev.ghen.thirst}）后端实现（{@link ThirstBridge}）。
 * <p>与新版「Thirst was Reclaimed」（{@code cn.mlus.thirst}）类名/方法签名完全一致、仅根包不同；
 * 两版 modId 均为 {@code thirst}，故 {@link ThirstBridge#get()} 按“类是否存在”选择后端。
 * <p>旧版本已作为依赖导入，这里直接调用其 API（不再使用反射）。
 */
public final class ThirstCompatLegacy implements ThirstBridge {

    public static final ThirstCompatLegacy INSTANCE = new ThirstCompatLegacy();

    private ThirstCompatLegacy() {}

    private static IThirst cap(Player player) {
        return player.getCapability(ModCapabilities.PLAYER_THIRST).orElse(null);
    }

    @Override
    public boolean loaded() {
        return true;
    }

    @Override
    public int primary(Player player) {
        IThirst c = cap(player);
        return c == null ? 20 : c.getThirst();
    }

    @Override
    public int secondary(Player player) {
        IThirst c = cap(player);
        return c == null ? 0 : c.getQuenched();
    }

    @Override
    public void add(Player player, int primary, int secondary, int purity) {
        IThirst c = cap(player);
        if (c == null) return;
        if (purity >= 0) WaterPurity.givePurityEffects(player, purity);
        c.drink(player, Math.max(1, primary), secondary);
    }

    @Override
    public boolean hydratesViaEat() {
        return true;
    }

    @Override
    public boolean restoresThirst(ItemStack stack) {
        return ThirstHelper.itemRestoresThirst(stack);
    }

    @Override
    public void drinkItem(ItemStack stack, Player player) {
        PlayerThirst.drink(stack, player);
    }

    @Override
    public boolean drinksOnFinish(Item item) {
        return item instanceof DrinkableItem;
    }

    @Override
    public void tagPurity(ItemStack stack, int purity) {
        if (stack == null || stack.isEmpty()) return;
        WaterPurity.addPurity(stack, purity);
    }

    @Override
    public int readPurity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        return WaterPurity.hasPurity(stack) ? WaterPurity.getPurity(stack) : -1;
    }
}
