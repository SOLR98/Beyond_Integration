package com.solr98.beyondintegration.feature.feeder;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.init.ModFluids;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.machine.FeederMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络喂食器的完整逻辑（由 Mixin {@code @Overwrite} 直接替换 BD 原 {@code workContent}）。
 * 补水档位与 BD 喂食档位同款语义；到档且（忽略饥饿 或 满足喂食档位）时补水，
 * 否则按原喂食档位喂食物。
 */
public final class FeederThirstHandler
{
    private FeederThirstHandler() {}

    /** 接管整段喂食逻辑（服务端调用） */
    public static void handle(ItemStack stack, Level level, Entity holder)
    {
        if (!(holder instanceof Player player)) return;

        ThirstBridge bridge = ThirstBridge.get();
        if (!bridge.loaded())
        {
            feedFood(stack, level, player, false);
            return;
        }

        // 回血模式：持续补满饥饿饱和度与水饱和，加速自然回血
        if (FeederThirstSettings.isRegenMode(stack))
        {
            handleRegen(stack, level, player, bridge);
            return;
        }

        // 1) 先正常喂食物（饥饿）
        feedFood(stack, level, player, false);

        // 2) 再看口渴：按顺序（Thirst → LSO）逐个后端“检测→补水”
        FeederThirstMode mode = FeederThirstSettings.getThirstMode(stack);
        var net = NetedItem.getNet(stack);
        if (net != null)
        {
            UnifiedStorage storage = net.getUnifiedStorage();
            List<KeyAmount> filterSlots = stack.getOrDefault(BDDataComponents.ISTACK_SLOTS, new ArrayList<>());
            List<ThirstBridge> backends = bridge.backends();
            for (ThirstBridge b : backends)
            {
                if (!mode.matches(b.primary(player), b.secondary(player))) continue;
                KeyAmount candidate = findCandidate(b, storage, filterSlots);
                if (candidate != null) consume(b, storage, candidate, player, level);
                else tryDrinkFluid(storage, filterSlots, player, b, level, false, backends.size());
            }
        }
    }

    /**
     * 回血模式：饥饿/口渴任一"未满或饱和度不足"就补充，把饥饿饱和度与水饱和都维持到
     * {@code feeder_thirst.regen_min_saturation} 以上（并保持饱食度=20、口渴=20），以加速自然回血。
     */
    private static void handleRegen(ItemStack stack, Level level, Player player, ThirstBridge bridge)
    {
        // 饥饿：饱食度未满或饱和度不足则喂食
        feedFood(stack, level, player, true);

        // 口渴：按顺序（Thirst → LSO）逐个后端“检测→补水”（额外补充水饱和）
        var net = NetedItem.getNet(stack);
        if (net != null)
        {
            UnifiedStorage storage = net.getUnifiedStorage();
            List<KeyAmount> filterSlots = stack.getOrDefault(BDDataComponents.ISTACK_SLOTS, new ArrayList<>());
            List<ThirstBridge> backends = bridge.backends();
            for (ThirstBridge b : backends)
            {
                if (b.primary(player) >= 20 && b.secondary(player) > CommandConfig.feederThirstRegenMinSaturation()) continue;
                KeyAmount candidate = findCandidate(b, storage, filterSlots);
                if (candidate != null) consume(b, storage, candidate, player, level);
                else tryDrinkFluid(storage, filterSlots, player, b, level, true, backends.size());
            }
        }
    }

    /**
     * 喂食物（复刻 BD 原 {@code NetFeederItem.workContent}）。
     *
     * @param regen 回血模式：饱食度/饱和未满即喂（忽略喂食档位）；否则按喂食档位判定
     */
    private static void feedFood(ItemStack stack, Level level, Player player, boolean regen)
    {
        FeederMode feederMode = stack.getOrDefault(BDDataComponents.FEEDER_MODE, FeederMode.NORMAL);
        List<KeyAmount> filterSlots = stack.getOrDefault(BDDataComponents.ISTACK_SLOTS, new ArrayList<>());
        FoodData data = player.getFoodData();
        if (regen)
        {
            if (data.getFoodLevel() >= 20 && data.getSaturationLevel() > (float) CommandConfig.feederThirstRegenMinSaturation()) return;
        }
        else if (!feederModeMatch(data, feederMode))
        {
            return;
        }

        var net = NetedItem.getNet(stack);
        if (net == null) return;
        UnifiedStorage storage = net.getUnifiedStorage();

        KeyAmount foodCache = null;
        boolean hungerFull = data.getFoodLevel() >= 20;
        for (KeyAmount filter : filterSlots)
        {
            if (filter == null || filter.isEmpty() || !(filter.key() instanceof ItemStackKey fk)) continue;
            for (KeyAmount stored : storage.getStorage())
            {
                if (!(stored.key() instanceof ItemStackKey sk) || !sk.isSame(fk)) continue;
                ItemStack candidate = sk.getReadOnlyStack();
                if (candidate.getFoodProperties(player) == null) continue;
                // 饱食度已满时，仅接受能补水的物品，避免无谓进食（把机会让给补水/流体）
                if (hungerFull && !ThirstBridge.get().restoresThirst(candidate)) continue;
                foodCache = new KeyAmount(stored.key(), 1L);
                break;
            }
            if (foodCache != null) break;
        }
        if (foodCache == null) return;

        KeyAmount extracted = storage.extract(foodCache.key(), 1L, false, false);
        if (extracted.isEmpty() || !(extracted.key() instanceof ItemStackKey key)) return;

        ItemStack foodStack = key.copyStackWithCount(1L);
        Item foodItem = foodStack.getItem();
        FoodProperties fp = foodItem.getFoodProperties(foodStack, player);
        if (fp != null && ((feederMode == FeederMode.SATURATION_KEEP && fp.saturation() > 0)
                || (feederMode != FeederMode.SATURATION_KEEP && fp.nutrition() > 0)))
        {
            ItemStack remaining = EventHooks.onItemUseFinish(player, foodStack.copy(), 0,
                    foodItem.finishUsingItem(foodStack, level, player));
            if (!remaining.isEmpty())
            {
                KeyAmount leftover = storage.insert(new ItemStackKey(remaining), remaining.getCount(), false);
                if (!leftover.isEmpty()) player.drop((ItemStack) leftover.toStack(), false);
            }
            return;
        }
        storage.insert(extracted.key(), extracted.amount(), false);
    }

    /**
     * 从网络流体中取水补水：找到第一份可饮用的水（本模组 4 档净化水，或原版水按配置纯度），
     * 每 {@code mbPerPoint} mB 恢复 1 点口渴，并按纯度施加 Thirst 的水纯度效果。
     */
    private static void tryDrinkFluid(UnifiedStorage storage, List<KeyAmount> filterSlots, Player player, ThirstBridge bridge, Level level, boolean regen, int backendCount)
    {
        for (KeyAmount stored : storage.getStorage())
        {
            if (!(stored.key() instanceof FluidStackKey fluidKey)) continue;
            // 只使用标记槽中标记过的流体
            if (!isFluidMarked(filterSlots, fluidKey)) continue;

            Fluid fluid = fluidKey.getSource();
            int purity = ModFluids.purityOf(fluid);
            if (purity < 0)
            {
                if (fluid == Fluids.WATER) purity = CommandConfig.feederThirstVanillaWaterPurity();
                else continue;
            }

            // 每 mbPerUse mB 水补一次；每次加 thirstPerUse 口渴、quenchedPerUse 水饱和
            int mbPerUse = Math.max(1, CommandConfig.feederThirstMbPerUse());
            if (stored.amount() < mbPerUse) continue;

            KeyAmount extracted = storage.extract(fluidKey, mbPerUse, false, false);
            if (extracted.isEmpty()) continue;

            int addThirst = Math.max(1, CommandConfig.feederThirstThirstPerUse(purity));
            int addQuenched = CommandConfig.feederThirstQuenchedPerUse(purity);
            if (regen) addQuenched = Math.max(1, addQuenched);
            // 不分开补充时，一份量在所有后端之间平摊
            if (backendCount > 1 && !CommandConfig.feederThirstSeparateHydration())
            {
                addThirst = Math.max(1, Math.round((float) addThirst / backendCount));
                addQuenched = Math.round((float) addQuenched / backendCount);
            }

            bridge.add(player, addThirst, addQuenched, purity);
            return;
        }
    }

    /** 该流体是否被标记槽中的某个流体标记匹配 */
    private static boolean isFluidMarked(List<KeyAmount> filterSlots, FluidStackKey fluidKey)
    {
        for (KeyAmount f : filterSlots)
        {
            if (f != null && f.key() instanceof FluidStackKey mark && mark.isSame(fluidKey)) return true;
        }
        return false;
    }

    /** 在标记槽位允许的范围内，寻找网络中的第一个「能补水」物品 */
    @Nullable
    private static KeyAmount findCandidate(ThirstBridge bridge, UnifiedStorage storage, List<KeyAmount> filterSlots)
    {
        if (filterSlots == null || filterSlots.isEmpty()) return null;
        for (KeyAmount filter : filterSlots)
        {
            if (filter == null || filter.isEmpty() || !(filter.key() instanceof ItemStackKey fk) || fk.isEmpty())
                continue;
            for (KeyAmount stored : storage.getStorage())
            {
                if (stored.key() instanceof ItemStackKey sk && sk.isSame(fk)
                        && bridge.restoresThirst(sk.getReadOnlyStack()))
                {
                    return new KeyAmount(stored.key(), 1L);
                }
            }
        }
        return null;
    }

    private static void consume(ThirstBridge bridge, UnifiedStorage storage, KeyAmount candidate, Player player, Level level)
    {
        KeyAmount extracted = storage.extract(candidate.key(), 1L, false, false);
        if (extracted.isEmpty() || !(extracted.key() instanceof ItemStackKey key)) return;

        ItemStack itemStack = key.copyStackWithCount(1L);
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        FoodProperties food = item.getFoodProperties(itemStack, player);
        ItemStack remaining;

        if (food != null)
        {
            // 食物：Thirst 经 Player.eat 混入自动补水；LSO 需显式补
            if (!bridge.hydratesViaEat() && bridge.restoresThirst(itemStack))
            {
                bridge.drinkItem(itemStack.copyWithCount(1), player);
            }
            remaining = EventHooks.onItemUseFinish(player, itemStack.copy(), 0,
                    item.finishUsingItem(itemStack, level, player));
        }
        else
        {
            // 纯饮料：先补水，再应用效果/产出容器
            if (!bridge.drinksOnFinish(item)) bridge.drinkItem(itemStack, player);
            ItemStack result = item.finishUsingItem(itemStack, level, player);
            // 若未消耗（返回同物品同组件），强制视为已饮用，避免无限补水
            if (ItemStack.isSameItemSameComponents(result, itemStack) && result.getCount() >= itemStack.getCount())
            {
                remaining = ItemStack.EMPTY;
            }
            else
            {
                remaining = result;
            }
        }

        if (!remaining.isEmpty())
        {
            KeyAmount leftover = storage.insert(new ItemStackKey(remaining), remaining.getCount(), false);
            if (!leftover.isEmpty())
            {
                player.drop((ItemStack) leftover.toStack(), false);
            }
        }
    }

    /** 与 BD {@code NetFeederItem#feederModeMatch} 等价的饥饿/饱和度判定 */
    private static boolean feederModeMatch(FoodData data, FeederMode mode)
    {
        return switch (mode)
        {
            case HUNGER_TO_EAT -> data.getFoodLevel() <= 2;
            case NORMAL -> data.getFoodLevel() <= 10;
            case SATURATION_KEEP -> data.getSaturationLevel() <= 0;
            case CRAZY -> data.getFoodLevel() < 20;
            default -> false;
        };
    }
}
