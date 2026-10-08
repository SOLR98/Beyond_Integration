package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import sfiomn.legendarysurvivaloverhaul.api.data.manager.ThirstDataManager;
import sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstCapability;
import sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil;

/**
 * Legendary Survival Overhaul 后端实现（{@link ThirstBridge}）。
 * <ul>
 *   <li>主值 = hydration（0–20），副值 = round(saturation)</li>
 *   <li>补水直接操作 capability（{@code addHydrationLevel}/{@code addSaturationLevel}），
 *       以绕过 LSO {@code takeDrink} 在满 hydration 时连 saturation 也不加的限制</li>
 *   <li>水纯度映射到 {@code HydrationEnum}：纯净→PURIFIED，其余→NORMAL</li>
 * </ul>
 * 仅在 {@code legendarysurvivaloverhaul} 加载时由 {@link ThirstBridge#get()} 选用。
 */
public final class LsoThirstBackend implements ThirstBridge
{
    public static final LsoThirstBackend INSTANCE = new LsoThirstBackend();

    private LsoThirstBackend() {}

    private static ThirstCapability cap(Player player)
    {
        return CapabilityUtil.getThirstCapability(player);
    }

    @Override
    public boolean loaded()
    {
        return ModList.get().isLoaded("legendarysurvivaloverhaul");
    }

    @Override
    public int primary(Player player)
    {
        return cap(player).getHydrationLevel();
    }

    @Override
    public int secondary(Player player)
    {
        return Math.round(cap(player).getSaturationLevel());
    }

    @Override
    public void add(Player player, int primary, int secondary, int purity)
    {
        ThirstCapability c = cap(player);
        if (!c.isHydrationLevelAtMax()) c.addHydrationLevel(Math.max(1, primary));
        if (secondary != 0) c.addSaturationLevel((float) secondary);
    }

    @Override
    public boolean hydratesViaEat()
    {
        // LSO 在 LivingEntityUseItemEvent.Finish 中对所有已注册 consumable 自动补水
        // （BI 食物分支会走 EventHooks/ForgeEventFactory.onItemUseFinish 触发该事件），
        // 故此处返回 true，避免 BI 再显式补一次导致重复。
        return true;
    }

    @Override
    public boolean restoresThirst(ItemStack stack)
    {
        return ThirstDataManager.getConsumable(stack) != null;
    }

    @Override
    public void drinkItem(ItemStack stack, Player player)
    {
        ThirstUtil.takeDrink(player, stack);
    }

    @Override
    public boolean drinksOnFinish(Item item)
    {
        return false;
    }

    @Override
    public void tagPurity(ItemStack stack, int purity)
    {
        if (stack == null || stack.isEmpty()) return;
        ThirstUtil.setHydrationEnumTag(stack, purity >= 3 ? HydrationEnum.PURIFIED : HydrationEnum.NORMAL);
    }

    @Override
    public int readPurity(ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return -1;
        HydrationEnum e = ThirstUtil.getHydrationEnumTag(stack);
        if (e == null) return -1;
        return e == HydrationEnum.PURIFIED ? 3 : 2;
    }
}
